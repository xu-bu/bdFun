package com.aritxonly.bdfun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.core.model.video.VideoItem
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun VideoLargeCard(
    video: VideoItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float = 0f,
    /** 首页推荐下点击 UP 名不跳转空间页。 */
    ownerClickEnabled: Boolean = true,
    /** 非空时在封面右上角显示缓存按钮。 */
    onCacheVideo: ((VideoItem) -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MiuixTheme.colorScheme
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(ContinuousCornerShape(14.dp))
            .background(colors.surfaceContainer)
            .combinedClickable(interactionSource = null, indication = null, onClick = onClick, onLongClick = onLongClick),
    ) {
        LargeVideoCover(video, progress, onCacheVideo)
        LargeVideoContent(video, ownerClickEnabled)
    }
}
