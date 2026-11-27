package com.aritxonly.bdfun.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aritxonly.bdfun.core.domain.DanmakuRuleMatcher
import com.aritxonly.bdfun.core.domain.filterDanmaku
import com.aritxonly.bdfun.core.model.video.DanmakuItem
import com.aritxonly.bdfun.feature.video.application.DanmakuModerationState
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 视频页「弹幕」tab：按时间排列的完整弹幕列表，点一条把视频跳到那里。
 * 过滤与播放器弹幕层同一套（屏蔽词、云端规则、撤回、样式屏蔽），列表里不会出现画面上被屏蔽的内容。
 * 懒列表只组合可见行；过滤/排序只在弹幕或规则变化时算一次，平时不随播放进度重组。
 */
@Composable
internal fun VideoDanmakuList(pane: com.aritxonly.bdfun.ui.VideoDanmakuPane) {
    val page = LocalVideoPageState.current
    val options = currentDanmakuLayerOptions(fullscreen = false)
    val moderation = page.danmakuModeration?.state?.collectAsState()?.value ?: DanmakuModerationState()
    val matcher = remember(moderation.rules) { DanmakuRuleMatcher(moderation.rules) }
    val items = remember(pane.items, options.filters, matcher, moderation.recalled, page.cid) {
        filterDanmaku(pane.items.filterNot { matcher.blocks(it) || (page.cid to it.id) in moderation.recalled }, options.filters)
    }
    val colors = MiuixTheme.colorScheme
    if (items.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(top = pane.topInset), contentAlignment = Alignment.TopCenter) {
            Text("暂无弹幕", color = colors.onBackgroundVariant, fontSize = 13.sp, modifier = Modifier.padding(top = 40.dp))
        }
        return
    }
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = pane.topInset, bottom = 32.dp),
    ) {
        item(key = "count") {
            Text("共 ${items.size} 条弹幕 · 点击跳转", color = colors.onBackgroundVariant, fontSize = 12.sp,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        }
        itemsIndexed(items, key = { index, item -> "${item.id}-${item.timeMs}-$index" }) { _, item ->
            VideoDanmakuRow(item) { page.dependencies.background.seekTo(item.timeMs) }
        }
    }
}

@Composable
private fun VideoDanmakuRow(item: DanmakuItem, onSeek: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSeek)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatDuration((item.timeMs / 1000L).toInt()),
            color = colors.primary,
            fontSize = 12.sp,
            modifier = Modifier.width(52.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(
            text = item.text,
            color = colors.onBackground,
            fontSize = 14.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (item.count > 1) {
            Spacer(Modifier.width(6.dp))
            Text("×${item.count}", color = colors.onBackgroundVariant, fontSize = 11.sp)
        }
    }
}
