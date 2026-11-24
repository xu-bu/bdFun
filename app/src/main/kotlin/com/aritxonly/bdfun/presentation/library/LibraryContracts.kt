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
    /** 收藏页「合集」据此定位看过的最远一集；为空时从第一页开始。 */
    val watchRecords: com.aritxonly.bdfun.core.application.WatchRecordStore? = null,
)
/** 收藏二级 tab 的展示数据，单独成组以控制 LibraryModeContent 主构造参数数量（上限 12）。 */
internal data class FavoriteSubContent(
    val tab: Int = 0, val folders: List<FavoriteFolder> = emptyList(),
    val bangumi: List<BangumiCard> = emptyList(), val ready: Boolean = false,
    val opened: OpenedFolderState? = null,
)
internal data class LibraryModeContent(
    val folders: List<FavoriteFolder>, val foldersReady: Boolean, val folderIndex: Int,
    val folderVideos: List<VideoItem>, val historyEntries: List<HistoryItem>, val watchLaterEntries: List<HistoryItem>,
    val loading: Boolean, val error: String?, val batchMode: Boolean, val selected: Set<String>, val canLoadMore: Boolean,
    val sub: FavoriteSubContent = FavoriteSubContent(),
) {
    // 读取兼容属性，保持 content.subTab 等既有写法不变。
    val subTab get() = sub.tab
    val subFolders get() = sub.folders
    val subBangumi get() = sub.bangumi
    val subReady get() = sub.ready
    val opened get() = sub.opened
}
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
    /** 合集从中间页打开时，滑到顶部补更早的一页。 */
    val onLoadPrevious: () -> Unit = {},
    /** 列表已定位到看过的最远一集。 */
    val onConsumeJump: () -> Unit = {},
)
