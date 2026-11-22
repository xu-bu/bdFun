package com.aritxonly.bdfun.ui

import androidx.compose.runtime.State
import com.aritxonly.bdfun.core.model.library.FavoriteFolder
import com.aritxonly.bdfun.core.model.library.HistoryItem
import com.aritxonly.bdfun.core.model.pgc.BangumiCard
import com.aritxonly.bdfun.core.model.video.VideoItem
import com.aritxonly.bdfun.feature.library.application.*

internal val MODES = listOf("收藏", "历史", "稍后再看")
internal val FAVORITE_SUB_TABS = listOf("默认收藏夹", "我创建的收藏夹", "我的收藏与订阅", "我的追番", "我的追剧")
internal data class LibraryScreenRequest(val loggedIn: Boolean, val owner: Long?, val fixedMode: Int? = null, val title: String = "收藏", val sessionRevision: Long = 0)
internal class LibraryNavigation(val onNeedLogin: () -> Unit, val onBack: () -> Unit, val onOpenVideo: (VideoItem) -> Unit)
internal class LibraryDependencies(
    val createData: () -> LibraryStateHolder,
    val createInteraction: (kotlinx.coroutines.CoroutineScope) -> LibraryInteractionStateHolder,
    val entrance: LibraryEntranceRegistry,
    val playbackQueue: com.aritxonly.bdfun.core.application.PlaybackQueueStateHolder,
)
internal data class LibraryModeContent(
    val folders: List<FavoriteFolder>, val foldersReady: Boolean, val folderIndex: Int,
    val folderVideos: List<VideoItem>, val historyEntries: List<HistoryItem>, val watchLaterEntries: List<HistoryItem>,
    val loading: Boolean, val error: String?, val batchMode: Boolean, val selected: Set<String>, val canLoadMore: Boolean,
    val subTab: Int = 0, val subFolders: List<FavoriteFolder> = emptyList(),
    val subBangumi: List<BangumiCard> = emptyList(), val subReady: Boolean = false,
    val opened: OpenedFolderState? = null,
)
internal data class LibraryModeLayout(
    val mode: Int, val title: String, val showModeTabs: Boolean, val isActive: Boolean,
    val modeProgress: State<Float>, val scrollTopTick: Int, val entrance: LibraryEntranceRegistry,
)
internal class LibraryModeActions(
    val owner: Any, val onSelectMode: (Int) -> Unit, val onToggleSelected: (String) -> Unit,
    val onFolderSelect: (Int) -> Unit, val onOpenVideo: (VideoItem) -> Unit, val onLoadMore: () -> Unit,
    val onPrepareQueue: (List<VideoItem>, String) -> Unit,
    val onSelectSubTab: (Int) -> Unit = {}, val onOpenFolder: (FavoriteFolder) -> Unit = {},
    val onCloseFolder: () -> Unit = {},
)
