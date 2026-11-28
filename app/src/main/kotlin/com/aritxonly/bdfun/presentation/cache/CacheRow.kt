package com.aritxonly.bdfun.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.core.model.cache.CachedVideo

/** 缓存条目内容：管理模式用勾选框替换右侧操作区，点击由外层统一处理。 */
@Composable
internal fun CacheRow(
    entry: CachedVideo,
    cover: String,
    onOpenDetail: () -> Unit,
    onRetry: () -> Unit,
    selection: @Composable (() -> Unit)? = null,
    actionsEnabled: Boolean = true,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CacheCover(cover)
        Spacer(Modifier.width(10.dp))
        CacheRowContent(entry)
        Spacer(Modifier.width(8.dp))
        if (selection != null) {
            selection()
        } else {
            CacheRowActions(entry.status, onOpenDetail, onRetry, enabled = actionsEnabled)
        }
    }
}
