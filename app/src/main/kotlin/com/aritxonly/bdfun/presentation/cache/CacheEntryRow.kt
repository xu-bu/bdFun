package com.aritxonly.bdfun.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.core.application.DownloadQueueStateHolder
import com.aritxonly.bdfun.core.model.cache.CacheStatus
import com.aritxonly.bdfun.core.model.video.VideoItem
import com.aritxonly.teak.common.settings.TeakSettingsCard
import top.yukonga.miuix.kmp.basic.Checkbox
import androidx.compose.ui.state.ToggleableState

/**
 * 单条缓存：长按进入管理模式；管理模式下点击=勾选，普通模式点击=播放。
 * 点击事件放在最外层，避免与内部「详情/重试」按钮的点击混淆。
 */
@Composable
internal fun CacheEntryRow(
    entry: com.aritxonly.bdfun.core.model.cache.CachedVideo,
    queue: DownloadQueueStateHolder,
    manageMode: Boolean,
    selected: Boolean,
    onEnterManage: () -> Unit,
    onToggleSelect: () -> Unit,
    onOpen: (VideoItem) -> Unit,
    onOpenDetail: (String) -> Unit,
) {
    val context = LocalContext.current
    val oneTake = rememberOneTakeAnchor()
    val cacheRadiusPx = with(LocalDensity.current) { 16.dp.toPx() }
    Box(
        modifier = Modifier
            .padding(bottom = 10.dp)
            .then(oneTake.modifier)
            .combinedClickable(
                interactionSource = null,
                indication = null,
                onClick = {
                    when {
                        manageMode -> onToggleSelect()
                        entry.status != CacheStatus.Completed -> Unit
                        else -> oneTake.open(
                            context = context,
                            video = queue.video(entry),
                            radiusPx = cacheRadiusPx,
                        ) { onOpen(queue.video(entry)) }
                    }
                },
                onLongClick = onEnterManage,
            ),
    ) {
        TeakSettingsCard {
            CacheRow(
                entry = entry,
                cover = queue.cover(entry),
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
                        context.showToast("暂时无法重试，可进入管理模式删除后重新缓存")
                    }
                },
                selection = if (manageMode) {
                    { CacheSelectCheckbox(selected) }
                } else {
                    null
                },
            )
        }
    }
}

@Composable
private fun CacheSelectCheckbox(selected: Boolean) {
    Checkbox(
        state = if (selected) ToggleableState.On else ToggleableState.Off,
        onClick = null,
    )
}
