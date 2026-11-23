package com.aritxonly.bdfun.ui

import android.content.Context
import com.aritxonly.bdfun.AppGraph
import com.aritxonly.bdfun.core.common.Outcome
import com.aritxonly.bdfun.core.common.messageOr
import com.aritxonly.bdfun.core.domain.VideoStreamRequest
import com.aritxonly.bdfun.core.domain.selectCacheQuality
import com.aritxonly.bdfun.core.model.video.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * 列表卡片「缓存」按钮的统一入口：动态流、首页推荐、搜索结果共用。
 * 列表项通常没有可用的 cid，先取详情再取有时效的直链，最后入下载队列。
 */
internal fun enqueueVideoCache(context: Context, scope: CoroutineScope, video: VideoItem) {
    val bvid = video.bvid.takeIf { it.isNotBlank() }
    if (bvid == null) {
        context.showToast("该视频暂无可缓存地址")
        return
    }
    scope.launch {
        val detail = when (val result = AppGraph.videoRepositoryForCast.videoDetail(bvid)) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> {
                context.showToast(result.error.messageOr("获取视频信息失败"))
                return@launch
            }
        }
        if (detail.cid <= 0L) {
            context.showToast("该视频暂无可缓存地址")
            return@launch
        }
        if (AppGraph.videoCache.findPlayable(bvid, detail.cid) != null) {
            context.showToast("已缓存")
            return@launch
        }
        val qualityId = AppGraph.settings.dynamicAutoPlayQuality
        val play = when (val result = AppGraph.videoPlaybackRepositoryForCast.playInfo(
            VideoStreamRequest(detail, qualityId, detail.cid, detail.duration,
                AppGraph.settings.codecPreference, AppGraph.settings.cdnService),
        )) {
            is Outcome.Success -> result.value
            is Outcome.Failure -> {
                context.showToast(result.error.messageOr("获取下载地址失败"))
                return@launch
            }
        }
        val quality = selectCacheQuality(play, qualityId)
        if (quality == null || play.audioUrl.isNullOrBlank()) {
            context.showToast("该视频暂时无法缓存")
            return@launch
        }
        val queued = AppGraph.videoCache.enqueue(
            detail = detail, quality = quality, cid = detail.cid,
            audioUrl = play.audioUrl, durationSec = detail.duration,
        )
        context.showToast(if (queued) "已加入缓存" else "该清晰度已在缓存中")
    }
}
