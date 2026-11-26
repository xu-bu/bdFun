package com.aritxonly.bdfun.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import kotlinx.coroutines.launch
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.dp
import com.aritxonly.bdfun.feature.library.application.*

@Composable
internal fun LibraryScreen(request: LibraryScreenRequest, navigation: LibraryNavigation, dependencies: LibraryDependencies) {
    val runtime = rememberLibraryRuntime(request, navigation, dependencies)
    LibraryEffects(runtime)
    if (!request.loggedIn) { LibraryGuest(navigation.onNeedLogin); return }
    LibraryContent(runtime)
}

@Composable
private fun LibraryContent(runtime: LibraryRuntime) {
    val data by androidx.compose.runtime.key(runtime.data) { runtime.data.state.collectAsState() }
    val interaction by androidx.compose.runtime.key(runtime.interaction) { runtime.interaction.state.collectAsState() }
    val pagerState = runtime.pager
    val progress = remember { derivedStateOf { pagerState.currentPage + pagerState.currentPageOffsetFraction } }
    Box(modifier = Modifier.fillMaxSize()) {
        CompositionLocalProvider(LocalViewConfiguration provides rememberStricterSwipeConfiguration()) {
            HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 0, overscrollEffect = null, pageSpacing = 12.dp) { page ->
                LibraryPagerPage(runtime, data, interaction, progress, runtime.request.fixedMode ?: page)
            }
        }
        if (interaction.searching) LibrarySearchOverlay(runtime, data, interaction)
        LibraryTransferSheet(runtime, data, interaction)
    }
}

@Composable
internal fun LibraryPagerPage(
    runtime: LibraryRuntime, data: LibraryUiState, interaction: LibraryInteractionState, progress: State<Float>, mode: Int,
) {
    // 收藏与订阅的排序依据；点开某个合集返回后，它会排到最前。
    val recentStore = runtime.dependencies.recentFolders
    val recentOpened by androidx.compose.runtime.remember(recentStore) {
        recentStore?.openedAt ?: kotlinx.coroutines.flow.flowOf(emptyMap())
    }.collectAsState(initial = androidx.compose.runtime.remember(recentStore) { recentStore?.snapshot().orEmpty() })
    LibraryModePage(
        content = libraryModeContent(runtime.interaction, data, interaction, mode, recentOpened),
        layout = LibraryModeLayout(mode, runtime.request.title, runtime.request.fixedMode == null,
            mode == runtime.currentMode, progress, runtime.presentation.scrollTopTick, runtime.dependencies.entrance),
        actions = LibraryModeActions(runtime.data, runtime::selectMode, runtime.interaction::toggleSelected,
            runtime::selectFolder, runtime.navigation.onOpenVideo, { runtime.loadMore(mode) }, runtime.dependencies.playbackQueue::set,
            runtime::selectSubTab, runtime::openFolder, runtime::closeFolder,
            onLoadPrevious = { runtime.scope.launch { runtime.data.loadPreviousOpened() } },
            onConsumeJump = runtime.data::consumeOpenedJump),
    )
}
internal fun libraryModeContent(
    interaction: LibraryInteractionStateHolder, data: LibraryUiState, ui: LibraryInteractionState, mode: Int,
    recentOpened: Map<String, Long> = emptyMap(),
) = LibraryModeContent(
    data.favorites.folders, data.favorites.ready, data.favorites.index,
    run {
        val opened = data.favorites.opened
        if (mode == 0 && opened != null) interaction.visibleVideos(opened.videos)
        else interaction.visibleVideos(data.favorites.videos)
    },
    interaction.visibleEntries(data.history.entries),
    interaction.visibleEntries(data.watchLater).take(ui.watchLaterVisibleCount), data.loading[mode] == true,
    data.errors[mode], ui.batchMode, ui.selected,
    when(mode) {
        0 -> when {
            data.favorites.opened != null -> data.favorites.opened!!.hasMore
            data.favorites.subTab == 1 -> data.favorites.created.hasMore
            data.favorites.subTab == 2 -> data.favorites.collected.hasMore
            data.favorites.subTab == 3 -> data.favorites.bangumi[1]?.hasMore == true
            data.favorites.subTab == 4 -> data.favorites.bangumi[2]?.hasMore == true
            else -> data.favorites.hasMore
        }
        1 -> data.history.hasMore
        else -> ui.watchLaterVisibleCount < data.watchLater.size
    },
    sub = FavoriteSubContent(
        tab = data.favorites.subTab,
        folders = when (data.favorites.subTab) {
            1 -> data.favorites.created.folders
            // 收藏与订阅：最近点开的排最前（本机记录，接口只按收藏时间返回）
            2 -> com.aritxonly.bdfun.core.domain.orderByRecentOpen(data.favorites.collected.folders, recentOpened)
            else -> emptyList()
        },
        bangumi = data.favorites.bangumi[if (data.favorites.subTab == 4) 2 else 1]?.items.orEmpty(),
        ready = when (data.favorites.subTab) {
            1 -> data.favorites.created.ready
            2 -> data.favorites.collected.ready
            3, 4 -> data.favorites.bangumi[if (data.favorites.subTab == 4) 2 else 1]?.ready == true
            else -> data.favorites.ready
        },
        opened = data.favorites.opened,
    ),
)

@Composable
internal fun LibrarySearchOverlay(runtime: LibraryRuntime, data: LibraryUiState, ui: LibraryInteractionState) {
    val interaction = runtime.interaction
    val results = when(runtime.currentMode) {
        0 -> interaction.visibleVideos(data.favorites.videos)
        1 -> interaction.visibleEntries(data.history.entries).map { it.toVideoItem() }
        else -> interaction.visibleEntries(data.watchLater).take(ui.watchLaterVisibleCount).map { it.toVideoItem() }
    }
    LibrarySearchPage(
        LibrarySearchContent(runtime.request.title, ui.searchHistory, results, data.loading[runtime.currentMode] == true, data.errors[runtime.currentMode]),
        LibrarySearchActions(interaction::clearSearchHistory, interaction::submitQuery, interaction::closeSearch, runtime.navigation.onOpenVideo),
    )
}

@Composable
internal fun LibraryTransferSheet(runtime: LibraryRuntime, data: LibraryUiState, ui: LibraryInteractionState) {
    val folders = data.favorites.folders
    val folderIndex = data.favorites.index
    // 移动 / 复制：与视频页点「收藏」后弹出的收藏夹菜单同一套底部面板。
    val activeTransfer = ui.transferMode
    val sourceId = folders.getOrNull(folderIndex)?.id
    val targets = folders.filter { it.id != sourceId }
    com.aritxonly.bdfun.feature.library.presentation.FavoriteFolderSheet(
        show = activeTransfer != null,
        state = com.aritxonly.bdfun.feature.library.presentation.FavoriteFolderSheetUiState(
            title = if (activeTransfer == 1) "移动到收藏夹" else "复制到收藏夹",
            description = "选择目标收藏夹 · 已选择 ${ui.selected.size} 个视频",
            folders = targets.map { folder ->
                com.aritxonly.bdfun.feature.library.presentation.FavoriteFolderOption(
                    folder.id, folder.title, folder.count,
                )
            },
            emptyMessage = "没有其他可用的收藏夹",
        ),
        onDismiss = runtime.interaction::dismissTransfer,
        onSelect = { id -> targets.firstOrNull { it.id == id }?.let { runtime.transfer(activeTransfer ?: 2, it) } },
    )
}
