package com.aritxonly.bdfun.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aritxonly.teak.common.settings.TeakSettingsPage
import top.yukonga.miuix.kmp.theme.MiuixTheme

import com.aritxonly.bdfun.core.application.DownloadQueueStateHolder
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@Composable
internal fun CacheScreen(
    queue: DownloadQueueStateHolder,
    onOpen: (com.aritxonly.bdfun.core.model.video.VideoItem) -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by queue.state.collectAsState()
    var revealedId by remember { mutableStateOf<String?>(null) }

    if (entries.isEmpty()) {
        CacheEmptyPage(modifier)
        return
    }

    TeakSettingsPage(
        title = "离线缓存",
        subtitle = "共 ${entries.size} 个 · ${formatCacheSize(entries.sumOf { it.totalBytes })}",
        modifier = modifier,
    ) {
        items(entries, key = { it.id }) { entry ->
            CacheEntryRow(
                entry = entry,
                queue = queue,
                revealed = revealedId == entry.id,
                onReveal = { revealedId = entry.id },
                onCollapse = { if (revealedId == entry.id) revealedId = null },
                onOpen = onOpen,
                onOpenDetail = onOpenDetail,
            )
        }
    }
}

/** 标题照常渲染，但空提示覆盖整屏居中，不受大标题占位影响。 */
@Composable
private fun CacheEmptyPage(modifier: Modifier) {
    val colors = MiuixTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize()) {
        TeakSettingsPage(title = "离线缓存", subtitle = null, modifier = modifier) {}
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
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
}
