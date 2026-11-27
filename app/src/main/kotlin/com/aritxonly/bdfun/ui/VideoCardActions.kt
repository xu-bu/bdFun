package com.aritxonly.bdfun.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.AppGraph
import com.aritxonly.bdfun.core.common.Outcome
import com.aritxonly.bdfun.core.common.messageOr
import com.aritxonly.bdfun.core.model.video.VideoItem
import com.kyant.shapes.Capsule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Download
import top.yukonga.miuix.kmp.icon.extended.SearchDevice
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 视频卡片信息区右下角的「稍后再看 / 缓存」按钮：放在文字区，不再盖住封面。
 * 稍后再看图标与播放器「更多」面板一致（SearchDevice）。
 */
@Composable
internal fun VideoCardActionButtons(video: VideoItem, onCache: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        VideoCardActionIcon(MiuixIcons.SearchDevice, "稍后再看") { addVideoToWatchLater(context, scope, video) }
        VideoCardActionIcon(MiuixIcons.Download, "缓存", onCache)
    }
}

@Composable
private fun VideoCardActionIcon(icon: ImageVector, description: String, onClick: () -> Unit) {
    // 28dp 触摸区、16dp 图标：比封面上的旧按钮小，和 UP 名一行也不显得挤。
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(Capsule())
            .clickable(interactionSource = null, indication = null, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, description, modifier = Modifier.size(16.dp), tint = MiuixTheme.colorScheme.onBackgroundVariant)
    }
}

/** 列表卡片「稍后再看」统一入口：有 aid 直接添加，只有 BV 号时先取详情拿 aid。 */
internal fun addVideoToWatchLater(context: Context, scope: CoroutineScope, video: VideoItem) {
    scope.launch {
        val aid = video.aid.takeIf { it > 0L } ?: when (val detail = AppGraph.videoRepositoryForCast.videoDetail(video.bvid)) {
            is Outcome.Success -> detail.value.aid
            is Outcome.Failure -> { context.showToast(detail.error.messageOr("获取视频信息失败")); return@launch }
        }
        when (val result = AppGraph.videoActionsRepository.watchLater(aid)) {
            is Outcome.Success -> context.showToast("已加入稍后再看")
            is Outcome.Failure -> context.showToast(result.error.messageOr("加入稍后再看失败"))
        }
    }
}
