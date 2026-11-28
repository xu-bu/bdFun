package com.aritxonly.bdfun.core.domain

import com.aritxonly.bdfun.core.model.video.PlayInfo
import com.aritxonly.bdfun.core.model.video.Quality

/**
 * 缓存固定取最高可用清晰度：qualities 已按 qn 降序（见 PlayInfoMapper），
 * 因此取第一个带直链的档位即可，与用户当前播放的清晰度无关。
 * 全部 DASH 档位无直链时才用 playurl 的 progressive 兜底地址补齐，
 * 避免把不可下载的档位交给队列。
 */
fun selectCacheQuality(info: PlayInfo): Quality? {
    val highest = info.qualities.firstOrNull { it.videoUrl.isNotBlank() }
    if (highest != null) return highest
    val fallback = info.fallbackUrl?.takeIf { it.isNotBlank() } ?: return null
    val top = info.qualities.firstOrNull() ?: return null
    return top.copy(videoUrl = fallback)
}
