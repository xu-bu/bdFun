package com.aritxonly.bdfun.core.domain.cast

/**
 * 投屏发现相关的纯文本解析：SSDP 响应、UPnP 设备描述 XML。
 * 不依赖 Android / OkHttp / JSON 库，便于 JVM 单测。infra:cast 负责收发报文。
 *
 * 目标协议是 B 站 NVA（哔哩必连）：设备在 SSDP / XML 里额外声明 NirvanaControl 服务，
 * 扫描页据此判断能否投屏；真正播放走 TCP 长连接（见 NvaFrames / NvaClient）。
 */

/** SSDP M-SEARCH 的响应头（大小写不敏感地读取）。 */
data class SsdpResponse(val location: String, val usn: String, val st: String) {
    /** SSDP 的 ST/USN 是否直接声明了 NVA（NirvanaControl）服务。 */
    val isNva: Boolean get() = st.contains(NIRVANA_SERVICE, true) || usn.contains(NIRVANA_SERVICE, true)
}

const val NIRVANA_SERVICE = "NirvanaControl"

/**
 * 解析一条 SSDP 响应：状态行必须是 HTTP/1.1 200，且必须带 LOCATION。
 * 设备用 LOCATION 指向自己的描述文档，缺它就无法继续。
 */
fun parseSsdpResponse(raw: String): SsdpResponse? {
    val lines = raw.lineSequence().map { it.trim('\r') }
    val status = lines.firstOrNull() ?: return null
    if (!status.contains(" 200 ", ignoreCase = true) && !status.endsWith(" 200", ignoreCase = true)) return null
    val headers = HashMap<String, String>(8)
    lines.drop(1).forEach { line ->
        val colon = line.indexOf(':')
        if (colon > 0) {
            headers[line.substring(0, colon).trim().lowercase()] = line.substring(colon + 1).trim()
        }
    }
    val location = headers["location"]?.takeIf { it.isNotBlank() } ?: return null
    return SsdpResponse(
        location = location,
        usn = headers["usn"].orEmpty(),
        st = headers["st"].orEmpty(),
    )
}

/** 从设备描述 XML 里解析出的身份信息；null 表示这不是一台可用的投屏渲染设备。 */
data class ParsedCastDescription(
    val udn: String,
    val name: String,
    val deviceType: String,
    val manufacturer: String,
    val modelName: String,
    /** 是否声明了 NirvanaControl 服务——B 站 NVA 投屏的必要条件。 */
    val isNva: Boolean,
)

/**
 * 解析 UPnP 设备描述（description.xml）。
 *
 * 判定依据：deviceType 是 MediaRenderer，或服务列表里含 AVTransport；
 * 再从中识别 NirvanaControl 服务（B 站 NVA）或 manufacturer 含 bilibili。
 * 只有 UDN 是必需的——它作为设备唯一身份，缺失则视为无效。
 * 不用通用 XML 库：设备描述是简单扁平结构，逐标签取值即可，避免为此加依赖。
 */
fun parseCastDescription(xml: String): ParsedCastDescription? {
    val udn = firstTag(xml, "UDN").ifBlank { firstTag(xml, "udn") }
    if (udn.isBlank()) return null
    val deviceType = firstTag(xml, "deviceType")
    val hasAvTransport = xml.contains("AVTransport", ignoreCase = true)
    val isRenderer = deviceType.contains("MediaRenderer", ignoreCase = true)
    if (!hasAvTransport && !isRenderer) return null
    val manufacturer = firstTag(xml, "manufacturer")
    val isNva = xml.contains(NIRVANA_SERVICE, ignoreCase = true) ||
        manufacturer.contains("bilibili", ignoreCase = true)
    val rawName = firstTag(xml, "friendlyName")
    return ParsedCastDescription(
        udn = udn,
        name = rawName.ifBlank { "未命名设备" },
        deviceType = deviceType,
        manufacturer = manufacturer,
        modelName = firstTag(xml, "modelName"),
        isNva = isNva,
    )
}

/**
 * 从设备描述文档 URL（LOCATION）里提取 host。
 *
 * NVA 的 TCP 连接直接用这个 host + 固定端口 9958，不使用 XML 里的 controlURL。
 * 兼容 LOCATION 里带端口（http://192.168.1.20:8200/desc.xml）与不带端口两种情况。
 */
fun extractHost(location: String): String? = try {
    val url = java.net.URL(location)
    val host = url.host
    host.takeIf { it.isNotBlank() }
} catch (_: Exception) {
    null
}

/** 取同名标签的文本内容，兼容带命名空间前缀（如 <device:friendlyName>）和自闭合空标签。 */
private fun firstTag(xml: String, tag: String): String {
    val regex = Regex(
        "<(?:[A-Za-z_][\\w.-]*:)?$tag(?:\\s[^>]*)?>(.*?)</(?:[A-Za-z_][\\w.-]*:)?$tag\\s*>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    val value = regex.find(xml)?.groupValues?.get(1).orEmpty()
    return decodeXmlEntities(value.trim())
}

private fun decodeXmlEntities(value: String): String = value
    .replace("&lt;", "<")
    .replace("&gt;", ">")
    .replace("&quot;", "\"")
    .replace("&apos;", "'")
    .replace("&amp;", "&")
