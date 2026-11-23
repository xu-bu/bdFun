package com.aritxonly.bdfun.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aritxonly.teak.common.settings.TeakSettingsCard
import com.aritxonly.teak.common.settings.TeakSettingsPage
import top.yukonga.miuix.kmp.theme.MiuixTheme

import com.aritxonly.bdfun.core.application.DownloadQueueStateHolder
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue

@Composable
internal fun CacheScreen(
    queue: DownloadQueueStateHolder,
    onOpen: (com.aritxonly.bdfun.core.model.video.VideoItem) -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MiuixTheme.colorScheme
    val context = LocalContext.current
    val entries by queue.state.collectAsState()

    if (entries.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize()) {
            // 标题照常渲染，但空提示覆盖整屏居中，不受大标题占位影响。
            TeakSettingsPage(title = "离线缓存", subtitle = null, modifier = modifier) {}
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "还没有缓存的视频",
                    color = colors.onBackground,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Normal,
                )
                Text(
                    text = "在播放器「更多」中选择「缓存」即可下载",
                    modifier = Modifier.padding(top = 6.dp),
                    color = colors.onBackgroundVariant,
                    fontSize = 12.sp,
                )
            }
        }
        return
    }

    TeakSettingsPage(
        title = "离线缓存",
        subtitle = "共 ${entries.size} 个 · ${formatCacheSize(entries.sumOf { it.totalBytes })}",
        modifier = modifier,
    ) {
        items(entries, key = { it.id }) { entry ->
            val oneTake = rememberOneTakeAnchor()
            val cacheRadiusPx = with(LocalDensity.current) { 16.dp.toPx() }
            TeakSettingsCard(
                modifier = Modifier.padding(bottom = 10.dp).then(oneTake.modifier),
            ) {
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
                            context.showToast("暂时无法重试，请删除后重新缓存")
                        }
                    },
                    onDelete = {
                        queue.delete(entry.id)
                        context.showToast("已删除缓存")
                    },
                )
            }
        }
    }
}
