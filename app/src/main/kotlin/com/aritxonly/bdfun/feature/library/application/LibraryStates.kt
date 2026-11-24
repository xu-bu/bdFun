package com.aritxonly.bdfun.feature.library.application

import com.aritxonly.bdfun.core.model.library.FavoriteFolder
import com.aritxonly.bdfun.core.model.library.HistoryItem
import com.aritxonly.bdfun.core.model.pgc.BangumiCard
import com.aritxonly.bdfun.core.model.video.VideoItem

internal data class FolderListState(
    val folders: List<FavoriteFolder> = emptyList(), val page: Int = 1,
    val hasMore: Boolean = false, val ready: Boolean = false, val revision: Int = 0,
)
internal data class BangumiListState(
    val items: List<BangumiCard> = emptyList(), val page: Int = 1,
    val hasMore: Boolean = false, val ready: Boolean = false, val revision: Int = 0,
)
internal data class OpenedFolderState(
    val folder: FavoriteFolder, val videos: List<VideoItem> = emptyList(), val page: Int = 1,
    val hasMore: Boolean = false, val loadingMore: Boolean = false, val ready: Boolean = false,
    val revision: Int = 0,
    /** 已加载的最早一页；合集从看过的最远一集所在页打开时大于 1，向上滑动再补更早的页。 */
    val firstPage: Int = 1,
    /** 打开后要定位到的视频（合集看过的最远一集）；列表滚动到位后清空。 */
    val jumpTo: String? = null,
) {
    val hasPrevious get() = firstPage > 1
}

/** 收藏页二级 tab 的独立状态；单独成组以控制 FavoriteUiState 主构造参数数量（审计上限 12）。 */
internal data class FavoriteSubState(
    val tab: Int = 0, val revision: Int = 0, val defaultFolderId: Long = 0L,
    val created: FolderListState = FolderListState(), val collected: FolderListState = FolderListState(),
    val bangumi: Map<Int, BangumiListState> = emptyMap(), val opened: OpenedFolderState? = null,
)

internal data class FavoriteUiState(
    val folders: List<FavoriteFolder> = emptyList(), val index: Int = 0,
    val videos: List<VideoItem> = emptyList(), val page: Int = 1,
    val hasMore: Boolean = false, val loadingMore: Boolean = false, val ready: Boolean = false,
    val revision: Int = 0,
    val sub: FavoriteSubState = FavoriteSubState(),
) {
    // 读取兼容属性：让 favorites.subTab 等既有写法保持不变，只需改构造/copy 调用点。
    val subTab get() = sub.tab
    val subRevision get() = sub.revision
    val defaultFolderId get() = sub.defaultFolderId
    val created get() = sub.created
    val collected get() = sub.collected
    val bangumi get() = sub.bangumi
    val opened get() = sub.opened
}
internal data class HistoryUiState(
    val entries: List<HistoryItem> = emptyList(), val max: Long = 0, val viewAt: Long = 0,
    val business: String = "", val hasMore: Boolean = false, val loadingMore: Boolean = false,
    val revision: Int = 0,
)
internal data class LibraryUiState(
    val favorites: FavoriteUiState = FavoriteUiState(), val history: HistoryUiState = HistoryUiState(),
    val watchLater: List<HistoryItem> = emptyList(), val loading: Map<Int, Boolean> = emptyMap(),
    val errors: Map<Int, String?> = emptyMap(), val busy: Boolean = false,
)
