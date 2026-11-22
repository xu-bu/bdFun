package com.aritxonly.bdfun.infra.cast

import com.aritxonly.bdfun.core.common.AppError
import com.aritxonly.bdfun.core.common.Outcome
import com.aritxonly.bdfun.core.domain.cast.NvaFrame
import com.aritxonly.bdfun.core.domain.cast.NvaFrameDecoder
import com.aritxonly.bdfun.core.domain.cast.encodeNvaCommand
import com.aritxonly.bdfun.core.domain.cast.encodeNvaReply
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket
import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

/**
 * B 站 NVA（哔哩必连）TCP 长连接客户端。
 *
 * 流程：连 TV 的 9958 端口 → 发 SETUP NVA/1.0 文本头握手 → 之后收发二进制帧。
 * TV 每秒发 ping（0xe4），也主动推状态指令（0xe0，如 OnProgress / OnPlayState）；
 * 客户端收到都要回 0xc0。上层只需关心 sendCommand / tvFrames。
 *
 * 全部 IO 在 io dispatcher；close() 幂等，断开后读循环自然退出。
 * 帧的字节编解码在 core:domain（纯 JVM，有单测），这里只做传输与连接生命周期。
 */
class NvaClient(
    private val io: CoroutineDispatcher = Dispatchers.IO,
) {
    /** TV 推来的帧（状态变化、ping 等），供上层更新进度/播放状态。 */
    val tvFrames: SharedFlow<NvaFrame> get() = _tvFrames
    private val _tvFrames = MutableSharedFlow<NvaFrame>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** TV 对我们指令的回复帧，供上层做失败诊断（含错误码）。 */
    val replies: SharedFlow<NvaFrame.Reply> get() = _replies
    private val _replies = MutableSharedFlow<NvaFrame.Reply>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 连接断开（主动或被动）事件，供上层决定重连或提示。 */
    val disconnected: SharedFlow<String> get() = _disconnected
    private val _disconnected = MutableSharedFlow<String>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private var socket: Socket? = null
    private var out: OutputStream? = null
    private var readJob: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + io)
    private val seq = AtomicInteger(0)
    @Volatile private var closed = true

    /**
     * 建立连接并握手。session / 客户端 UUID 每次连接生成，符合协议要求
     * （session 用标准 UUID；客户端 UUID 格式为 Y + 35 位 [0-1A-Z]）。
     */
    suspend fun connect(host: String, port: Int = DEFAULT_PORT): Outcome<Unit> = withContext(io) {
        if (!closed) {
            // 已连接则复用；换设备时上层先 close()。
            return@withContext Outcome.Success(Unit)
        }
        try {
            val sock = Socket()
            sock.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT_MS)
            sock.soTimeout = 0 // 读循环用阻塞读 + 关 socket 唤醒，不靠 soTimeout
            sock.tcpNoDelay = true
            val session = UUID.randomUUID().toString()
            val deviceUuid = newClientUuid()
            out = sock.getOutputStream()
            out?.write(buildSetup("SETUP", session, deviceUuid, host, port))
            out?.flush()
            if (!waitHandshake(sock)) {
                val detail = lastHandshake.trim().replace(Regex("\\s+"), " ").take(160)
                sock.close()
                return@withContext Outcome.Failure(
                    AppError.Network("电视拒绝了投屏连接（握手失败）${if (detail.isEmpty()) "" else "：$detail"}")
                )
            }
            socket = sock
            closed = false
            seq.set(0)
            readJob = scope.launch { readLoop(sock) }
            Outcome.Success(Unit)
        } catch (e: Exception) {
            Outcome.Failure(AppError.Network("连接电视失败：${e.message ?: "未知错误"}"))
        }
    }

    /** 发一条指令（带可选 JSON payload）。序号从 1 单调递增。 */
    suspend fun sendCommand(name: String, json: String? = null): Outcome<Unit> = withContext(io) {
        val stream = out
        if (closed || stream == null) {
            return@withContext Outcome.Failure(AppError.Unknown("投屏连接已断开"))
        }
        try {
            synchronized(stream) {
                val next = seq.incrementAndGet()
                stream.write(encodeNvaCommand(next, name, json))
                stream.flush()
            }
            Outcome.Success(Unit)
        } catch (e: Exception) {
            Outcome.Failure(AppError.Network("发送指令失败：${e.message ?: "未知错误"}"))
        }
    }

    /**
     * 停止播放并断开：必须先发 Stop 再关 socket，且不能挂在调用方的 lifecycleScope 上
     * （退出页面 finish() 会取消那个 scope，Stop 帧根本发不出去，电视继续播）。
     * 这里用客户端自己的 SupervisorJob scope，独立于 Activity 生命周期；
     * 发完 Stop 稍等一拍让电视处理，再 close。幂等。
     */
    fun stopAndClose() {
        scope.launch {
            if (!closed) {
                runCatching { sendCommand("Stop") }
                delay(STOP_FLUSH_DELAY_MS)
            }
            close()
        }
    }

    /** 主动断开。幂等。 */
    fun close() {
        if (closed) return
        closed = true
        runCatching { socket?.close() }
        readJob?.cancel()
        readJob = null
        socket = null
        out = null
    }

    private suspend fun readLoop(sock: Socket) {
        val decoder = NvaFrameDecoder()
        val input = BufferedInputStream(sock.getInputStream(), 8192)
        val buffer = ByteArray(8192)
        try {
            while (!closed) {
                val n = input.read(buffer)
                if (n < 0) break // 对端关闭
                decoder.feed(buffer.copyOfRange(0, n)).forEach { frame ->
                    when (frame) {
                        is NvaFrame.Ping -> reply(frame.seq) // ping 也要回 c0，否则 TV 判定离线
                        is NvaFrame.Command -> {
                            // TV 主动推的指令（OnProgress 等）：先回执，再上报。
                            reply(frame.seq)
                            _tvFrames.emit(frame)
                        }
                        is NvaFrame.Reply -> _replies.tryEmit(frame) // 上层记录，便于失败时看 TV 返回的错误码
                    }
                }
            }
        } catch (_: Exception) {
            // 连接被关闭（含我们主动 close）会抛异常，属正常
        } finally {
            val wasOpen = !closed
            closed = true
            socket = null
            out = null
            if (wasOpen) _disconnected.emit("投屏连接已断开")
        }
    }

    private fun reply(seqNum: Int) {
        val stream = out ?: return
        runCatching {
            synchronized(stream) {
                stream.write(encodeNvaReply(seqNum))
                stream.flush()
            }
        }
    }

    /** 读 SETUP 的文本响应，直到空行（HTTP 风格头）；确认含 200 OK。 */
    private fun waitHandshake(sock: Socket): Boolean {
        val input = BufferedInputStream(sock.getInputStream(), 1024)
        val response = StringBuilder()
        try {
            while (response.length < 4096) {
                val c = input.read()
                if (c < 0) return false
                response.append(c.toChar())
                // 头部以空行结束：CRLF CRLF 或 LF LF
                if (response.endsWith("\r\n\r\n") || response.endsWith("\n\n")) break
            }
        } catch (_: Exception) {
            return false
        }
        val ok = response.contains("200 OK", ignoreCase = true) ||
            response.contains("NVA/1.0 200", ignoreCase = true)
        if (!ok) lastHandshake = response.toString()
        return ok
    }

    /** 客户端 UUID：Y + 35 位 [0-1A-Z]。协议要求同一设备保持不变，这里会话内固定。 */
    private fun newClientUuid(): String {
        val alphabet = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ"
        val sb = StringBuilder("Y")
        repeat(35) { sb.append(alphabet[(Math.random() * alphabet.length).toInt()]) }
        return sb.toString()
    }

    private fun buildSetup(method: String, session: String, uuid: String, host: String, port: Int): ByteArray =
        ("$method /projection NVA/1.0\r\n" +
            "Session: $session\r\n" +
            "NvaVersion: 1\r\n" +
            "Connection: Keep-Alive\r\n" +
            "UUID: $uuid\r\n" +
            "User-Agent: Linux/3.0.0 UPnP/1.0 Platinum/1.0.5.13\r\n" +
            "Host: $host:$port\r\n" +
            "\r\n").toByteArray(Charsets.UTF_8)

    companion object {
        const val DEFAULT_PORT = 9958
        private const val CONNECT_TIMEOUT_MS = 5_000
        /** Stop 写出后给电视的处理窗口，避免 socket 立刻关闭导致 Stop 未被消费。 */
        private const val STOP_FLUSH_DELAY_MS = 250L
        /** 握手失败时保存原文，便于上层日志排查。 */
        @Volatile var lastHandshake: String = ""
            private set
    }
}
