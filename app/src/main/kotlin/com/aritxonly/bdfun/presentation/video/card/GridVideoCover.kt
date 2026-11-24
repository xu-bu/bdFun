package com.aritxonly.bdfun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.aritxonly.bdfun.core.model.video.VideoItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GridVideoCover(
    video: VideoItem, progress: Float, selectionEnabled: Boolean, selected: Boolean, reserveMeta: Boolean,
    onCacheVideo: ((VideoItem) -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .background(colors.surfaceContainerHigh),
    ) {
        AsyncImage(
            model = rememberImageRequest(video.cover),
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(42.dp)
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, Color(0x4D000000))),
                ),
        )

        GridCoverMetrics(video, reserveMeta)

        // 已看进度：本机观看记录优先，历史记录页传入的云端进度兜底
        WatchProgressBar(video.bvid, progress)

        GridCoverSelection(selectionEnabled, selected)
        if (onCacheVideo != null && !selectionEnabled) VideoCacheButton { onCacheVideo(video) }
    }

}
