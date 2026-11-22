package com.aritxonly.bdfun.core.domain

import com.aritxonly.bdfun.core.model.video.PlayInfo
import com.aritxonly.bdfun.core.model.video.Quality

/**
 * 缓存时挑选清晰度：优先用户偏好，其次 720P，再退列表最后一项。
 * 直链为空时用 playurl 的兜底地址补齐，避免把不可下载的质量交给队列。
 */
fun selectCacheQuality(info: PlayInfo, preferredQuality: Int): Quality? {
    val quality = info.qualities.firstOrNull { it.id == preferredQuality }
        ?: info.qualities.firstOrNull { it.id == 64 }
        ?: info.qualities.lastOrNull()
        ?: return null
    val url = quality.videoUrl.takeIf { it.isNotBlank() }
        ?: info.fallbackUrl?.takeIf { it.isNotBlank() }
        ?: return null
    return if (url == quality.videoUrl) quality else quality.copy(videoUrl = url)
}
