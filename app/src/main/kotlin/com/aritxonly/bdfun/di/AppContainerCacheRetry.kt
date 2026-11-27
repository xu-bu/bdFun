package com.aritxonly.bdfun.di

import com.aritxonly.bdfun.core.common.AppError
import com.aritxonly.bdfun.core.common.Outcome
import com.aritxonly.bdfun.core.application.CacheRetryUrls
import com.aritxonly.bdfun.core.domain.VideoStreamRequest

/** 缓存失败项重试：B 站直链有时效，必须现取 videoDetail + playInfo 才能重新下载。 */
internal fun AppContainer.configureCacheRetry() {
    val cache = videoCache ?: return
    // 缓存时一并保存弹幕，离线播放直接读本地文件。
    cache.setDanmakuSource { cid, durationSec ->
        (videoPlaybackRepository.danmaku(cid, durationSec) as? Outcome.Success)?.value
    }
    cache.setRetryResolver { entry ->
        val detail = when (val r = videoRepository.videoDetail(entry.bvid)) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return@setRetryResolver Outcome.Failure(r.error)
        }
        val play = when (val r = videoPlaybackRepository.playInfo(
            VideoStreamRequest(detail, entry.qualityId, entry.cid, entry.durationSec,
                settings.codecPreference, settings.cdnService))) {
            is Outcome.Success -> r.value
            is Outcome.Failure -> return@setRetryResolver Outcome.Failure(r.error)
        }
        val videoUrl = play.qualities.firstOrNull { it.id == entry.qualityId }?.videoUrl
            ?: play.fallbackUrl
        val audioUrl = play.audioUrl
        if (videoUrl.isNullOrBlank() || audioUrl.isNullOrBlank()) {
            Outcome.Failure(AppError.Parsing("该清晰度暂无可用下载地址"))
        } else {
            Outcome.Success(CacheRetryUrls(videoUrl, audioUrl))
        }
    }
}
