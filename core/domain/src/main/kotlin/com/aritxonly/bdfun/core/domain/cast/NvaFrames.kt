package com.aritxonly.bdfun.core.domain.cast

import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * B 站 NVA（哔哩必连）协议的二进制帧编解码，纯 JVM，可单测。
 *
 * NVA 是跑在 TCP 上的长连接协议（默认端口 9958），帧有三种：
 * - 0xe0 指令：客户端和 TV 都能发，携带命令名与可选 JSON payload
 * - 0xc0 回复：对指令的应答，seq 用对方指令的序号
 * - 0xe4 ping：TV 每秒发一次保活
 *
 * 4 字节整数字段全部是大端（network byte order）。TCP 是字节流，会粘包/半包，
 * 所以解码器必须增量喂数据：不足一帧时缓存，凑齐再产出。
 */
sealed class NvaFrame {
    /** 0xe0 指令帧。name 如 Play / GetVolume / OnProgress。 */
    data class Command(val seq: Int, val name: String, val json: String?) : NvaFrame()
    /** 0xc0 回复帧。json 为 null 表示空回复（"无返回值"即参数为空，不是不回）。 */
    data class Reply(val seq: Int, val json: String?) : NvaFrame()
    /** 0xe4 ping 帧，TV 端每秒发一次。 */
    data class Ping(val seq: Int) : NvaFrame()
}

private const val TAG_COMMAND: Byte = 0xe0.toByte()
private const val TAG_REPLY: Byte = 0xc0.toByte()
private const val TAG_PING: Byte = 0xe4.toByte()
private val COMMAND_LITERAL = "Command".toByteArray(Charsets.US_ASCII)

/**
 * 编码客户端指令（0xe0）。
 * argc=2：Command + 命令名；argc=3：再加一个 JSON 参数（4 字节长度前缀）。
 */
fun encodeNvaCommand(seq: Int, name: String, json: String? = null): ByteArray {
    require(seq > 0) { "NVA 指令序号从 1 起" }
    val nameBytes = name.toByteArray(Charsets.UTF_8)
    val jsonBytes = json?.toByteArray(Charsets.UTF_8)
    val argc = if (jsonBytes == null) 2 else 3
    val size = 1 + 1 + 4 + 1 + (1 + COMMAND_LITERAL.size) + (1 + nameBytes.size) +
        (if (jsonBytes != null) 4 + jsonBytes.size else 0)
    return ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN).apply {
        put(TAG_COMMAND)
        put(argc.toByte())
        putInt(seq)
        put(0x01) // 固定字节，疑似协议版本 v1
        put(COMMAND_LITERAL.size.toByte()).put(COMMAND_LITERAL)
        put(nameBytes.size.toByte()).put(nameBytes)
        if (jsonBytes != null) {
            putInt(jsonBytes.size)
            put(jsonBytes)
        }
    }.array()
}

/** 编码客户端回复（0xc0）。seq 填对方指令的序号。 */
fun encodeNvaReply(seq: Int, json: String? = null): ByteArray {
    val jsonBytes = json?.toByteArray(Charsets.UTF_8)
    val argc = if (jsonBytes == null) 0 else 1
    val size = 1 + 1 + 4 + (if (jsonBytes != null) 4 + jsonBytes.size else 0)
    return ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN).apply {
        put(TAG_REPLY)
        put(argc.toByte())
        putInt(seq)
        if (jsonBytes != null) {
            putInt(jsonBytes.size)
            put(jsonBytes)
        }
    }.array()
}

/**
 * 增量帧解码器。feed() 接收 TCP 字节块，返回其中凑齐的帧；半包留到下次。
 * 遇到未知标签的垃圾字节会丢弃 1 字节重新同步，避免一个坏字节卡死整条连接。
 */
class NvaFrameDecoder {
    private val buffer = ByteArrayOutputStream()

    fun feed(chunk: ByteArray): List<NvaFrame> {
        if (chunk.isNotEmpty()) buffer.write(chunk)
        val out = ArrayList<NvaFrame>()
        while (true) {
            val bytes = buffer.toByteArray()
            if (bytes.isEmpty()) break
            if (bytes[0] != TAG_COMMAND && bytes[0] != TAG_REPLY && bytes[0] != TAG_PING) {
                // 未知标签：丢 1 字节重新同步
                replaceBuffer(bytes, 1)
                continue
            }
            val parsed = when (bytes[0]) {
                TAG_COMMAND -> parseCommand(bytes)
                TAG_REPLY -> parseReply(bytes)
                TAG_PING -> parsePing(bytes)
                else -> null
            } ?: break // 数据不足，等下一块
            out += parsed.first
            replaceBuffer(bytes, parsed.second)
        }
        return out
    }

    private fun replaceBuffer(bytes: ByteArray, consumed: Int) {
        buffer.reset()
        if (consumed < bytes.size) buffer.write(bytes, consumed, bytes.size - consumed)
    }

    private fun parseCommand(b: ByteArray): Pair<NvaFrame, Int>? {
        if (b.size < 8) return null // e0 + argc + seq(4) + 0x01
        val argc = b[1].toInt() and 0xff
        // 布局：e0(1) + argc(1) + seq(4) + 0x01(1)，长度前缀从 index 7 开始
        var pos = 7
        // 参数1必须是 "Command"（1 字节长度前缀）
        val p1 = readLenPrefixed(b, pos, 1) ?: return null
        pos = p1.second
        val literal = String(b, p1.first, p1.third, Charsets.UTF_8)
        if (literal != "Command") return null
        // 参数2：命令名（1 字节长度前缀）
        val p2 = readLenPrefixed(b, pos, 1) ?: return null
        pos = p2.second
        val name = String(b, p2.first, p2.third, Charsets.UTF_8)
        // 参数3：可选 JSON（4 字节长度前缀）
        var json: String? = null
        if (argc >= 3) {
            val p3 = readLenPrefixed(b, pos, 4) ?: return null
            pos = p3.second
            json = String(b, p3.first, p3.third, Charsets.UTF_8)
        }
        return NvaFrame.Command(readInt(b, 2), name, json) to pos
    }

    private fun parseReply(b: ByteArray): Pair<NvaFrame, Int>? {
        if (b.size < 6) return null
        val argc = b[1].toInt() and 0xff
        var pos = 6
        var json: String? = null
        if (argc >= 1) {
            val p = readLenPrefixed(b, pos, 4) ?: return null
            pos = p.second
            json = String(b, p.first, p.third, Charsets.UTF_8)
        }
        return NvaFrame.Reply(readInt(b, 2), json) to pos
    }

    private fun parsePing(b: ByteArray): Pair<NvaFrame, Int>? {
        if (b.size < 6) return null
        return NvaFrame.Ping(readInt(b, 2)) to 6
    }

    /** 返回 Triple(内容起始下标, 内容之后的下标, 内容长度)。 */
    private fun readLenPrefixed(b: ByteArray, pos: Int, lenBytes: Int): Triple<Int, Int, Int>? {
        if (pos + lenBytes > b.size) return null
        val len = (0 until lenBytes).fold(0) { acc, i -> (acc shl 8) or (b[pos + i].toInt() and 0xff) }
        val start = pos + lenBytes
        if (start + len > b.size) return null
        return Triple(start, start + len, len)
    }

    private fun readInt(b: ByteArray, pos: Int): Int =
        ((b[pos].toInt() and 0xff) shl 24) or ((b[pos + 1].toInt() and 0xff) shl 16) or
            ((b[pos + 2].toInt() and 0xff) shl 8) or (b[pos + 3].toInt() and 0xff)
}
