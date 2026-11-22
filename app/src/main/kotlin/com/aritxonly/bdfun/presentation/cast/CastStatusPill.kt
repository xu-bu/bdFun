package com.aritxonly.bdfun.presentation.cast

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.AppGraph
import com.aritxonly.teak.common.icons.AppGlyph
import com.aritxonly.teak.common.icons.LucideIcons

/**
 * 投屏常驻状态按钮：投屏中在视频页右上角显示"正在投屏到 XXX"，
 * 点击即停止投屏（内部会关连接、恢复手机音量）。
 * 放在 scaffold 的 overlay slot，属于 BoxScope，能盖在播放器之上。
 */
@Composable
internal fun BoxScope.CastStatusPill() {
    val session = AppGraph.castSession
    val state by session.state.collectAsState()
    if (state.status != CastStatus.Playing) return
    val colors = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = colors.primaryContainer,
        contentColor = colors.onPrimaryContainer,
        shadowElevation = 3.dp,
        modifier = Modifier
            .align(Alignment.TopEnd)
            .statusBarsPadding()
            .padding(top = 8.dp, end = 16.dp)
            .clickable { session.disconnect() },
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            AppGlyph(icon = LucideIcons.Cast, tint = colors.onPrimaryContainer, size = 18.dp)
            Spacer(Modifier.width(8.dp))
            Text(
                text = "投屏中 · ${state.device?.name.orEmpty()}",
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
