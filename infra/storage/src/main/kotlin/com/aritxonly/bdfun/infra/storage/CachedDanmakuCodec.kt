package com.aritxonly.bdfun.infra.storage

import com.aritxonly.bdfun.core.model.video.DanmakuItem
import org.json.JSONArray
import org.json.JSONObject

/**
 * 离线缓存的弹幕文件（每个缓存条目目录下的 danmaku.json）。
 * 短键名控制体积；「自己刚发的」只是本次会话的渲染标记，不落盘。解析失败返回 null，由调用方联网补弹幕。
 */
internal fun encodeCachedDanmaku(items: List<DanmakuItem>): String {
    val array = JSONArray()
    items.forEach { item ->
        array.put(JSONObject()
            .put("t", item.timeMs).put("m", item.mode).put("c", item.color).put("s", item.size.toDouble())
            .put("x", item.text).put("i", item.id).put("l", item.likeCount).put("n", item.count)
            .put("u", item.userHash).put("v", item.vipColor).put("p", item.upIdentity))
    }
    return JSONObject().put("version", 1).put("items", array).toString()
}

internal fun decodeCachedDanmaku(raw: String): List<DanmakuItem>? = runCatching {
    val array = JSONObject(raw).getJSONArray("items")
    (0 until array.length()).map { index ->
        val o = array.getJSONObject(index)
        DanmakuItem(
            timeMs = o.getLong("t"), mode = o.getInt("m"), color = o.getInt("c"), size = o.getDouble("s").toFloat(),
            text = o.getString("x"), id = o.optLong("i"), likeCount = o.optLong("l"), count = o.optInt("n"),
            userHash = o.optString("u"), vipColor = o.optBoolean("v"), upIdentity = o.optBoolean("p"),
        )
    }
}.getOrNull()
