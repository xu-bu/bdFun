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
import top.yukonga.miuix.kmp.theme.MiuixTheme


@Composable
internal fun CacheRowActions(
    status: com.aritxonly.bdfun.core.model.cache.CacheStatus,
    onOpen: () -> Unit,
    onOpenDetail: () -> Unit,
    onRetry: () -> Unit,
    onDelete: () -> Unit,
) {
    val colors = MiuixTheme.colorScheme
    Column(horizontalAlignment = Alignment.End) {
        if (status == com.aritxonly.bdfun.core.model.cache.CacheStatus.Completed) {
            Text(
                text = "播放",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(interactionSource = null, indication = null, onClick = onOpen)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = BrandPink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
            )
        } else if (status == com.aritxonly.bdfun.core.model.cache.CacheStatus.Failed) {
            Text(
                text = "重试",
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(interactionSource = null, indication = null, onClick = onRetry)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                color = BrandPink,
                fontSize = 12.sp,
                fontWeight = FontWeight.Normal,
            )
        }
        // 详情走在线路由：只带 BV 号，让 VideoActivity 自己拉完整详情/评论；
        // 与上方的「播放」区分——播放优先使用本地缓存并立刻起播。
        Text(
            text = "详情",
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(interactionSource = null, indication = null, onClick = onOpenDetail)
                .padding(horizontal = 8.dp, vertical = 3.dp),
            color = BrandPink,
            fontSize = 11.sp,
            fontWeight = FontWeight.Normal,
        )
        Text(
            text = "删除",
            modifier = Modifier
                .clip(RoundedCornerShape(8.dp))
                .clickable(
                    interactionSource = null,
                    indication = null,
                    onClick = onDelete,
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
            color = colors.onBackgroundVariant,
            fontSize = 11.sp,
        )
    }
}
