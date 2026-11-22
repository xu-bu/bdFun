package com.aritxonly.bdfun.core.domain.cast

import com.aritxonly.bdfun.core.model.video.DanmakuItem

/**
 * NVA 各命令的 JSON payload 组装，纯 JVM 便于单测。
 *
 * 不引 JSON 库：payload 都是扁平的少量字段，手写拼接 + 严格转义即可，
 * 让 core:domain 保持零第三方依赖。字段类型严格对齐 NVA 接收端——
 * 身份字段（aid/cid/oid/epId/...）是字符串，seekTs/qn 是数字。
 *
 * 字段名以官方 NVA Play 报文为准（Macast nirvana 插件 / kodi-bilibili-cast
 * 的接收端解析、BiliSail 对云视听 1.8.9 的静态核对三处一致）：
 * 之前用 snake_case（access_key/content_type/...）时 TV 读不到这些字段，
 * 且缺少 autoNext/isOpen/mobileVersion 时云视听会走"空直链"路径，
 * 表现为"加载失败（错误码：null）"。
 */

/** 构造 Play 命令参数。TV 自己按 aid/cid 取流，手机不推 URL。 */
fun nvaPlayPayload(
    aid: Long,
    cid: Long,
    quality: Int,
    seekSec: Int = 0,
    accessKey: String = "",
    speed: Float = 1f,
    danmakuOn: Boolean = true,
): String = buildJson {
    // 内容身份：UGC 视频 epId/seasonId/roomId 为 0，oid 用 aid。
    str("aid", aid.toString())
    str("cid", cid.toString())
    str("oid", aid.toString())
    str("epId", "0")
    str("seasonId", "0")
    str("roomId", "0")
    str("contentType", "0") // 0=UGC 普通视频，1=番剧，3=直播
    raw("type", "0")
    raw("biz_id", "0")
    // 会话标识（真实手机客户端会带；接收端一般只做完整性检查）
    str("biz_session_id", java.util.UUID.randomUUID().toString())
    str("sessionId", java.util.UUID.randomUUID().toString())
    str("proj_type", "1")
    str("accessCode", "")
    str("startKey", "")
    raw("desc", "1")
    // 身份解析开关：云视听 1.8.9 靠 autoNext/an 决定是否按 aid/cid 自己取流。
    // 缺失会降级到空直链，立刻"加载失败（错误码：null）"。
    str("autoNext", "true")
    raw("an", "true")
    str("isOpen", danmakuOn.toString())
    raw("io", danmakuOn.toString())
    // 时间单位：mobileVersion/mv >= 6240000 表示 seekTs 用秒，旧版按毫秒。
    str("mobileVersion", "6240000")
    raw("mv", "6240000")
    raw("seekTs", seekSec.toString())
    str("sk", seekSec.toString())
    // 画质 / 倍速 / 弹幕偏好（camelCase 才是 NVA 接收端读取的键）
    raw("qn", quality.toString())
    raw("userDesireQn", quality.toString())
    str("userDesireSpeed", speed.toString())
    raw("danmakuSwitchSave", danmakuOn.toString())
    str("accessKey", accessKey)
}

fun nvaSeekPayload(seekSec: Int) = buildJson { raw("seekTs", seekSec.toString()) }
fun nvaQualityPayload(qn: Int) = buildJson { str("qn", qn.toString()) }
fun nvaSpeedPayload(speed: String) = buildJson { str("speed", speed) }
fun nvaDanmakuSwitchPayload(open: Boolean) = buildJson { str("open", open.toString()) }
fun nvaVolumePayload(volume: Int) = buildJson { raw("volume", volume.toString()) }

/**
 * 把一条本地弹幕转成 NVA SendDanmaku 参数。
 *
 * B 站 mode：1 滚动 / 4 底部 / 5 顶部 / 6 逆向 / 7 高级 / 8 代码 / 9 BAS；
 * NVA type：1 滚动 / 5 上侧中央 / 4 下侧中央。非标准模式降级为滚动。
 * NVA size 只有 18/25/36 三档（本地 size 是 1f/1.5f/2f 之类）。
 * mRemoteDmId 用服务端弹幕 id；0 时用 timeMs 派生一个稳定的负数，避免 TV 端把它当有效 id。
 */
fun nvaSendDanmakuPayload(item: DanmakuItem): String {
    val type = when (item.mode) {
        5 -> 5
        4 -> 4
        else -> 1
    }
    val size = when {
        item.size >= 2f -> 36
        item.size >= 1.4f -> 25
        else -> 18
    }
    val remoteId = if (item.id != 0L) item.id else -(item.timeMs + 1)
    return buildJson {
        raw("size", size.toString())
        raw("mRemoteDmId", remoteId.toString())
        str("content", item.text)
        str("action", "")
        raw("type", type.toString())
        raw("color", item.color.toString()) // 十进制 RGB
    }
}

/** 从 TV 推来的 OnProgress JSON 里取 position/duration（秒）。做宽松解析，缺字段给 0。 */
fun parseNvaProgress(json: String?): Pair<Int, Int> {
    if (json.isNullOrBlank()) return 0 to 0
    return parseJsonLong(json, "position").toInt() to parseJsonLong(json, "duration").toInt()
}

/** 从 TV 推来的 OnPlayState JSON 里取 playState；取不到返回 null。 */
fun parseNvaPlayState(json: String?): Int? {
    if (json.isNullOrBlank()) return null
    val v = parseJsonLong(json, "playState")
    return if (json.contains("\"playState\"")) v.toInt() else null
}

/** TV 报告的一个可选清晰度档位（来自 OnQnSwitch / OnEpisodeSwitch.qnDesc.supportQnList）。 */
data class NvaQualityOption(
    val qn: Int,
    val label: String,
    val superscript: String = "",
    val needVip: Boolean = false,
)

/** TV 报告的当前画质状态：curQn 是实际生效档位，options 是该片源支持的档位。 */
data class NvaQualityInfo(
    val curQn: Int,
    val options: List<NvaQualityOption>,
)

/**
 * 解析 TV 推来的画质帧。OnQnSwitch 直接带 curQn/supportQnList；
 * OnEpisodeSwitch 把同样的内容包在 qnDesc 里。两种结构都能从整段 JSON 里取到。
 * 取不到 curQn 返回 null（让上层保留旧值），但支持列表仍尽量解析。
 */
fun parseNvaQuality(json: String?): NvaQualityInfo? {
    if (json.isNullOrBlank()) return null
    val hasCur = json.contains("\"curQn\"")
    val hasList = json.contains("\"supportQnList\"")
    if (!hasCur && !hasList) return null

    val options = ArrayList<NvaQualityOption>()
    val listMarker = "\"supportQnList\""
    val listIdx = json.indexOf(listMarker)
    if (listIdx >= 0) {
        val bracket = json.indexOf('[', listIdx + listMarker.length)
        if (bracket >= 0) {
            val close = findMatchingBracket(json, bracket)
            if (close > bracket) splitJsonObjects(json.substring(bracket + 1, close)).forEach { obj ->
                val qn = parseJsonLong(obj, "quality").toInt()
                if (qn <= 0) return@forEach
                val label = parseJsonStringValue(obj, "displayDesc")
                    .ifBlank { parseJsonStringValue(obj, "description") }
                options += NvaQualityOption(
                    qn = qn,
                    label = label.ifBlank { qn.toString() },
                    superscript = parseJsonStringValue(obj, "superscript"),
                    needVip = parseJsonBoolValue(obj, "needVip"),
                )
            }
        }
    }
    val curQn = if (hasCur) parseJsonLong(json, "curQn").toInt() else 0
    return NvaQualityInfo(curQn = curQn, options = options)
}

/** 找到与 start（'['）配对的 ']'，跳过字符串内的括号。 */
private fun findMatchingBracket(json: String, start: Int): Int {
    var depth = 0
    var inString = false
    var escaped = false
    for (i in start until json.length) {
        val ch = json[i]
        if (inString) {
            if (escaped) escaped = false
            else if (ch == '\\') escaped = true
            else if (ch == '"') inString = false
        } else {
            when (ch) {
                '"' -> inString = true
                '[' -> depth++
                ']' -> {
                    depth--
                    if (depth == 0) return i
                }
            }
        }
    }
    return -1
}

/** 把 JSON 数组体按顶层对象拆开（对象内部是扁平字段，不会嵌套）。 */
private fun splitJsonObjects(body: String): List<String> {
    val result = ArrayList<String>()
    var depth = 0
    var start = -1
    var inString = false
    var escaped = false
    for (i in body.indices) {
        val ch = body[i]
        if (inString) {
            if (escaped) escaped = false
            else if (ch == '\\') escaped = true
            else if (ch == '"') inString = false
        } else {
            when (ch) {
                '"' -> inString = true
                '{' -> {
                    if (depth == 0) start = i
                    depth++
                }
                '}' -> {
                    depth--
                    if (depth == 0 && start >= 0) {
                        result += body.substring(start, i + 1)
                        start = -1
                    }
                }
            }
        }
    }
    return result
}

/** 极简 JSON 取字符串值：找 "key" 后的第一个引号串，处理常见转义。 */
private fun parseJsonStringValue(json: String, key: String): String {
    val marker = "\"$key\""
    val idx = json.indexOf(marker)
    if (idx < 0) return ""
    val colon = json.indexOf(':', idx + marker.length)
    if (colon < 0) return ""
    var i = colon + 1
    while (i < json.length && json[i].isWhitespace()) i++
    if (i >= json.length || json[i] != '"') return ""
    i++
    val sb = StringBuilder()
    while (i < json.length) {
        val ch = json[i]
        if (ch == '\\' && i + 1 < json.length) {
            when (json[i + 1]) {
                '"' -> sb.append('"')
                '\\' -> sb.append('\\')
                'n' -> sb.append('\n')
                'r' -> sb.append('\r')
                't' -> sb.append('\t')
                else -> sb.append(json[i + 1])
            }
            i += 2
        } else if (ch == '"') break
        else {
            sb.append(ch)
            i++
        }
    }
    return sb.toString()
}

/** 极简 JSON 取布尔值。 */
private fun parseJsonBoolValue(json: String, key: String): Boolean {
    val marker = "\"$key\""
    val idx = json.indexOf(marker)
    if (idx < 0) return false
    val colon = json.indexOf(':', idx + marker.length)
    if (colon < 0) return false
    var i = colon + 1
    while (i < json.length && json[i].isWhitespace()) i++
    return json.startsWith("true", i)
}

/** 极简 JSON 取值：找 "key" 后的数字（含负号/小数则取整数部分）。 */
private fun parseJsonLong(json: String, key: String): Long {
    val marker = "\"$key\""
    val idx = json.indexOf(marker)
    if (idx < 0) return 0
    val colon = json.indexOf(':', idx + marker.length)
    if (colon < 0) return 0
    var i = colon + 1
    while (i < json.length && json[i].isWhitespace()) i++
    val start = i
    if (i < json.length && (json[i] == '-' || json[i] == '+')) i++
    while (i < json.length && json[i] in '0'..'9') i++
    if (i == start) return 0
    return json.substring(start, i).toLongOrNull() ?: 0
}

/** JSON 字符串转义，覆盖控制字符，避免弹幕内容里的引号/换行破坏报文。 */
private fun jsonEscape(value: String): String = buildString(value.length + 8) {
    for (ch in value) {
        when (ch) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (ch < ' ') append("\\u%04x".format(ch.code)) else append(ch)
        }
    }
}

private class JsonBuilder {
    private val parts = ArrayList<Pair<String, String>>()
    /** 字符串值：自动加引号并转义。 */
    fun str(key: String, value: String) {
        parts += jsonEscape(key) to "\"${jsonEscape(value)}\""
    }

    /** 裸值：数字或布尔，由调用方保证合法。 */
    fun raw(key: String, value: String) {
        parts += jsonEscape(key) to value
    }

    fun build(): String = parts.joinToString(",", "{", "}") { (k, v) -> "\"$k\":$v" }
}

private inline fun buildJson(block: JsonBuilder.() -> Unit): String = JsonBuilder().apply(block).build()
