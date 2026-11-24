package com.aritxonly.bdfun.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.core.application.DownloadQueueStateHolder
import com.aritxonly.bdfun.core.model.cache.CachedVideo
import com.aritxonly.teak.common.settings.TeakSettingsCard

/** 单条缓存：左滑进入管理模式（露出删除），点内容区收起或直接播放。 */
@Composable
internal fun CacheEntryRow(
    entry: CachedVideo,
    queue: DownloadQueueStateHolder,
    revealed: Boolean,
    onReveal: () -> Unit,
    onCollapse: () -> Unit,
    onOpen: (com.aritxonly.bdfun.core.model.video.VideoItem) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val context = LocalContext.current
    val oneTake = rememberOneTakeAnchor()
    val cacheRadiusPx = with(LocalDensity.current) { 16.dp.toPx() }
    CacheSwipeRow(
        isRevealed = revealed,
        onReveal = onReveal,
        onCollapse = onCollapse,
        onDelete = {
            onCollapse()
            queue.delete(entry.id)
            context.showToast("已删除缓存")
        },
        modifier = Modifier.padding(bottom = 10.dp).then(oneTake.modifier),
    ) {
        TeakSettingsCard {
            CacheRow(
                entry = entry,
                cover = queue.cover(entry),
                onOpen = {
                    oneTake.open(
                        context = context,
                        video = queue.video(entry),
                        radiusPx = cacheRadiusPx,
                    ) { onOpen(queue.video(entry)) }
                },
                onOpenDetail = {
                    val bvid = entry.bvid
                    if (bvid.isBlank()) {
                        context.showToast("该缓存缺少视频标识，无法打开详情")
                    } else {
                        onOpenDetail(bvid)
                    }
                },
                onRetry = {
                    if (queue.retry(entry.id)) {
                        context.showToast("正在重新缓存…")
                    } else {
                        context.showToast("暂时无法重试，请左滑删除后重新缓存")
                    }
                },
                interactive = !revealed,
            )
        }
    }
}
