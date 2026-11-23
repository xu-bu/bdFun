package com.aritxonly.bdfun.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.core.model.video.VideoItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

/** 共享卡片保持原anchor与绘制；首页模块只传入打开/长按动作。 */
@Composable
internal fun HomeFeedCard(video: VideoItem, largeCards: Boolean, onOpen: () -> Unit, onLongClick: () -> Unit) {
    val density = LocalDensity.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val colors = MiuixTheme.colorScheme
    val oneTake = rememberOneTakeAnchor()
    val onCache: (VideoItem) -> Unit = { enqueueVideoCache(context, scope, it) }
    val radiusPx = with(density) { (if (largeCards) 14.dp else 16.dp).toPx() }
    val bgArgb = colors.background.toArgb()
    val openCard = {
        oneTake.open(
            context = context,
            video = video,
            radiusPx = radiusPx,
            backgroundColor = bgArgb,
        ) { onOpen() }
    }
    if (largeCards) {
        VideoLargeCard(video = video, modifier = oneTake.modifier, onClick = openCard,
            ownerClickEnabled = false, onCacheVideo = onCache, onLongClick = onLongClick)
    } else {
        VideoGridCard(video = video, modifier = oneTake.modifier, onClick = openCard,
            ownerClickEnabled = false, onCacheVideo = onCache, onLongClick = onLongClick)
    }
}
