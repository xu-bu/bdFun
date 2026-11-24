package com.aritxonly.bdfun.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.aritxonly.bdfun.core.model.video.VideoItem

/** 共享视频行也用于空间页，其原转场/图片策略由app提供，避免feature间互相依赖。 */
class VideoDetailTailSlots(
    val cover: @Composable (String, Modifier) -> Unit,
    val relatedRow: @Composable (VideoItem, () -> Unit) -> Unit,
    /** 合集单集封面底部的已看进度条（按 bvid），观看记录由 app 提供。 */
    val watchProgress: @Composable androidx.compose.foundation.layout.BoxScope.(String) -> Unit = {},
)
