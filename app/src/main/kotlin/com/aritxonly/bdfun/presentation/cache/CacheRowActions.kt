package com.aritxonly.bdfun.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aritxonly.bdfun.BrandPink
import com.aritxonly.bdfun.core.model.cache.CacheStatus


@Composable
internal fun CacheRowActions(
    status: com.aritxonly.bdfun.core.model.cache.CacheStatus,
    onOpenDetail: () -> Unit,
    onRetry: () -> Unit,
    enabled: Boolean = true,
) {
    Column(horizontalAlignment = Alignment.End) {
        if (status == com.aritxonly.bdfun.core.model.cache.CacheStatus.Failed) {
            Text(
                text = "重试",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(
                        interactionSource = null,
                        indication = null,
                        enabled = enabled,
                        onClick = onRetry,
                    )
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = BrandPink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
            )
        }
        // 详情走在线路由：只带 BV 号，让 VideoActivity 自己拉完整详情/评论；
        // 本地起播改为点列表项，不再单独放「播放」按钮。
        Text(
            text = "详情",
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = null,
                    indication = null,
                    enabled = enabled,
                    onClick = onOpenDetail,
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
            color = BrandPink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}
