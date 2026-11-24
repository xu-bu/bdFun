package com.aritxonly.bdfun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.core.application.WatchRecordStore
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 本机观看记录；由各 Activity 外壳提供。未提供时（预览/测试）封面不显示已看进度。 */
internal val LocalWatchRecords = staticCompositionLocalOf<WatchRecordStore?> { null }

/**
 * 某个视频的已看比例：优先用本机观看记录（看完为满条），没有记录时退回调用方给的值
 * （例如历史记录接口返回的云端进度）。每张卡片只订阅自己的 bvid，别的视频更新时不重组。
 */
@Composable
internal fun rememberWatchFraction(bvid: String, fallback: Float = 0f): Float {
    val store = LocalWatchRecords.current
    if (store == null || bvid.isBlank()) return fallback
    val flow = remember(store, bvid) { store.records.map { it[bvid] }.distinctUntilChanged() }
    // 初值取当前快照，卡片首帧就画出进度条，不会先空一帧再出现。
    val initial = remember(store, bvid) { store.recordOf(bvid) }
    val record by flow.collectAsState(initial = initial)
    return record?.fraction?.takeIf { it > 0f } ?: fallback
}

/** 封面底部的已看进度条；与历史记录原有样式一致（2dp 主题色）。 */
@Composable
internal fun BoxScope.WatchProgressBar(fraction: Float) {
    if (fraction <= 0f) return
    Box(
        modifier = Modifier
            .align(Alignment.BottomStart)
            .fillMaxWidth(fraction.coerceIn(0f, 1f))
            .height(2.dp)
            .background(MiuixTheme.colorScheme.primary),
    )
}

@Composable
internal fun BoxScope.WatchProgressBar(bvid: String, fallback: Float = 0f) =
    WatchProgressBar(rememberWatchFraction(bvid, fallback))
