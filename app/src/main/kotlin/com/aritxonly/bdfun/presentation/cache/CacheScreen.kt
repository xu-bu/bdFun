package com.aritxonly.bdfun.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aritxonly.bdfun.core.application.DownloadQueueStateHolder
import com.aritxonly.teak.common.navigation.TeakBatchAction
import com.aritxonly.teak.common.navigation.TeakBatchActionBar
import com.aritxonly.teak.common.settings.TeakSettingsPage
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun CacheScreen(
    queue: DownloadQueueStateHolder,
    onOpen: (com.aritxonly.bdfun.core.model.video.VideoItem) -> Unit,
    onOpenDetail: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val entries by queue.state.collectAsState()
    val context = LocalContext.current
    var manageMode by remember { mutableStateOf(false) }
    var selectedIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    // 缓存被外部删除后同步清理选中态，避免删除动作作用到已不存在的条目。
    LaunchedEffect(entries) {
        val alive = entries.mapTo(HashSet()) { it.id }
        selectedIds = selectedIds.intersect(alive)
        if (alive.isEmpty()) manageMode = false
    }

    if (entries.isEmpty()) {
        CacheEmptyPage(modifier)
        return
    }

    Box(modifier) {
        TeakSettingsPage(
            title = "离线缓存",
            subtitle = "共 ${entries.size} 个 · ${formatCacheSize(entries.sumOf { it.totalBytes })}",
            modifier = Modifier.fillMaxSize(),
        ) {
            items(entries, key = { it.id }) { entry ->
                CacheEntryRow(
                    entry = entry,
                    queue = queue,
                    manageMode = manageMode,
                    selected = entry.id in selectedIds,
                    onEnterManage = {
                        if (!manageMode) {
                            manageMode = true
                            selectedIds = setOf(entry.id)
                        }
                    },
                    onToggleSelect = {
                        selectedIds = if (entry.id in selectedIds) selectedIds - entry.id else selectedIds + entry.id
                    },
                    onOpen = onOpen,
                    onOpenDetail = onOpenDetail,
                )
            }
        }
        CacheManageBar(
            visible = manageMode,
            selectedCount = selectedIds.size,
            allSelected = selectedIds.size == entries.size,
            onSelectAll = {
                selectedIds = if (selectedIds.size == entries.size) emptySet() else entries.mapTo(HashSet()) { it.id }
            },
            onDelete = {
                val ids = selectedIds.toList()
                ids.forEach(queue::delete)
                selectedIds = emptySet()
                manageMode = false
                context.showToast("已删除 ${ids.size} 个缓存")
            },
            onExit = {
                manageMode = false
                selectedIds = emptySet()
            },
        )
    }
}

@Composable
private fun BoxScope.CacheManageBar(
    visible: Boolean,
    selectedCount: Int,
    allSelected: Boolean,
    onSelectAll: () -> Unit,
    onDelete: () -> Unit,
    onExit: () -> Unit,
) {
    TeakBatchActionBar(
        visible = visible,
        selectedCount = selectedCount,
        actions = listOf(
            TeakBatchAction("all", if (allSelected) "取消全选" else "全选"),
            TeakBatchAction("delete", "删除", enabled = selectedCount > 0, danger = true),
            TeakBatchAction("exit", "完成"),
        ),
        onAction = { action ->
            when (action.key) {
                "all" -> onSelectAll()
                "delete" -> onDelete()
                "exit" -> onExit()
            }
        },
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = 14.dp),
    )
}

/** 标题照常渲染，但空提示覆盖整屏居中，不受大标题占位影响。 */
@Composable
private fun CacheEmptyPage(modifier: Modifier) {
    val colors = MiuixTheme.colorScheme
    Box(modifier = Modifier.fillMaxSize()) {
        TeakSettingsPage(title = "离线缓存", subtitle = null, modifier = modifier) {}
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = "还没有缓存的视频",
                color = colors.onBackground,
                fontSize = 16.sp,
                fontWeight = FontWeight.Normal,
            )
            Text(
                text = "在播放器「更多」中选择「缓存」即可下载",
                modifier = Modifier.padding(top = 6.dp),
                color = colors.onBackgroundVariant,
                fontSize = 12.sp,
            )
        }
    }
}
