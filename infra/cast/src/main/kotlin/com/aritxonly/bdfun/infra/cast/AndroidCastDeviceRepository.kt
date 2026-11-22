package com.aritxonly.bdfun.infra.cast

import android.content.Context
import android.net.wifi.WifiManager
import com.aritxonly.bdfun.core.common.AppError
import com.aritxonly.bdfun.core.common.Outcome
import com.aritxonly.bdfun.core.domain.cast.CastDeviceRepository
import com.aritxonly.bdfun.core.domain.cast.extractHost
import com.aritxonly.bdfun.core.domain.cast.parseCastDescription
import com.aritxonly.bdfun.core.domain.cast.parseSsdpResponse
import com.aritxonly.bdfun.core.model.cast.CastDevice
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.DatagramPacket
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.MulticastSocket
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * 基于 SSDP/UPnP 的局域网投屏设备发现。
 *
 * 两阶段：① 拿 WiFi 多播锁，向 239.255.255.250:1900 发两类 M-SEARCH
 * （标准 MediaRenderer + B 站 NirvanaControl），收集应答里的 LOCATION；
 * ② 关闭 socket 后并发拉各设备描述文档，交给 core:domain 的纯解析器识别 NVA 设备。
 * 分开是为了拉描述（HTTP，耗时）时不再占用 UDP 收包，避免漏掉其它设备应答。
 * 多播锁与 socket 都在 finally 释放，否则一直唤醒 WiFi、耗电。
 */
class AndroidCastDeviceRepository(
    private val context: Context,
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(2, TimeUnit.SECONDS)
        .readTimeout(2, TimeUnit.SECONDS)
        .callTimeout(3, TimeUnit.SECONDS)
        .build(),
) : CastDeviceRepository {

    override suspend fun discover(
        timeoutMs: Long,
        onDevice: (CastDevice) -> Unit,
    ): Outcome<Unit> = withContext(Dispatchers.IO) {
        val wifi = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
        var lock: WifiManager.MulticastLock? = null

        try {
            if (wifi == null) {
                return@withContext Outcome.Failure(AppError.Unknown("无法访问 WiFi，投屏需要连接局域网"))
            }
            lock = wifi.createMulticastLock("bdfun-cast").also {
                it.setReferenceCounted(true)
                it.acquire()
            }

            val locations = collectLocations(timeoutMs)

            val emittedUdns = HashSet<String>()
            coroutineScope {
                locations.map { location ->
                    async(Dispatchers.IO) {
                        fetchAndEmit(location) { device ->
                            synchronized(emittedUdns) {
                                if (emittedUdns.add(device.udn)) onDevice(device)
                            }
                        }
                    }
                }.forEach { it.await() }
            }
            Outcome.Success(Unit)
        } catch (e: Exception) {
            currentCoroutineContext().ensureActive()
            Outcome.Failure(AppError.Network("投屏设备搜索失败：${e.message ?: "未知错误"}"))
        } finally {
            runCatching { if (lock?.isHeld == true) lock!!.release() }
        }
    }

    /** 收包阶段：发两类 M-SEARCH，在窗口内收集去重的 LOCATION。 */
    private suspend fun collectLocations(timeoutMs: Long): List<String> {
        val socket = MulticastSocket(null).apply {
            reuseAddress = true
            bind(InetSocketAddress(0)) // 设备把应答单播回这个临时端口
            soTimeout = 300
        }
        val locations = LinkedHashSet<String>()
        try {
            val group = InetAddress.getByName(SSDP_ADDRESS)
            val probes = listOf(M_SEARCH_RENDERER, M_SEARCH_NVA).map { it.toByteArray(Charsets.UTF_8) }
            // 发两轮，丢包时仍能发现；间隔短，不拉长整体窗口。
            repeat(2) { round ->
                probes.forEach { probe ->
                    socket.send(DatagramPacket(probe, probe.size, group, SSDP_PORT))
                }
                if (round == 0) delay(150)
            }
            val deadline = System.currentTimeMillis() + timeoutMs
            while (System.currentTimeMillis() < deadline) {
                currentCoroutineContext().ensureActive()
                val response = try {
                    val buffer = ByteArray(2048)
                    val packet = DatagramPacket(buffer, buffer.size)
                    socket.receive(packet)
                    String(packet.data, packet.offset, packet.length, Charsets.UTF_8)
                } catch (_: SocketTimeoutException) {
                    continue
                }
                val ssdp = parseSsdpResponse(response) ?: continue
                locations.add(ssdp.location)
            }
        } finally {
            runCatching { socket.close() }
        }
        return locations.toList()
    }

    /** 拉一台设备的描述文档，确认是渲染设备后回调；单台失败不影响整轮搜索。 */
    private suspend fun fetchAndEmit(location: String, onDevice: (CastDevice) -> Unit) {
        val request = Request.Builder().url(location).get().build()
        val xml = try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return
                response.body?.string().orEmpty()
            }
        } catch (_: Exception) {
            return
        }
        if (xml.isBlank()) return
        val parsed = parseCastDescription(xml) ?: return
        val host = extractHost(location) ?: return
        currentCoroutineContext().ensureActive()
        onDevice(
            CastDevice(
                udn = parsed.udn,
                name = parsed.name,
                location = location,
                deviceType = parsed.deviceType,
                manufacturer = parsed.manufacturer,
                modelName = parsed.modelName,
                nvaHost = host,
                isNva = parsed.isNva,
            ),
        )
    }

    private companion object {
        const val SSDP_ADDRESS = "239.255.255.250"
        const val SSDP_PORT = 1900
        const val M_SEARCH_RENDERER = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: urn:schemas-upnp-org:device:MediaRenderer:1\r\n" +
            "\r\n"
        const val M_SEARCH_NVA = "M-SEARCH * HTTP/1.1\r\n" +
            "HOST: 239.255.255.250:1900\r\n" +
            "MAN: \"ssdp:discover\"\r\n" +
            "MX: 2\r\n" +
            "ST: urn:app-bilibili-com:service:NirvanaControl:3\r\n" +
            "\r\n"
    }
}
