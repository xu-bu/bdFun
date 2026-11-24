package com.aritxonly.bdfun.feature.library.application

import com.aritxonly.bdfun.core.common.*
import com.aritxonly.bdfun.core.domain.LibraryRepository
import com.aritxonly.bdfun.core.model.library.FavoriteFolder
import com.aritxonly.bdfun.core.model.video.VideoItem

/**
 * 收藏页里打开的单个收藏夹 / 订阅合集。
 * 合集可以从中间某一页开始加载（看过的最远一集所在页，例如 900 集里的第 43 页），
 * 向下照常翻页，向上再补更早的页；版本号隔离与原 loadOpened/loadMoreOpened 一致。
 */
internal class OpenedFolderPaging(private val repository: LibraryRepository, private val host: Host) {
    interface Host {
        val isActive: Boolean
        val current: LibraryUiState
        fun update(transform: (LibraryUiState) -> LibraryUiState)
        suspend fun <T> request(action: suspend () -> Outcome<T>): Outcome<T>
        fun loading(tab: Int, value: Boolean)
        fun error(tab: Int, value: String?)
    }

    fun open(folder: FavoriteFolder, startPage: Int = 1, jumpTo: String? = null) {
        if (!host.isActive) return
        val start = startPage.coerceAtLeast(1)
        host.update { it.copy(favorites = it.favorites.copy(sub = it.favorites.sub.copy(
            opened = OpenedFolderState(folder, page = start, firstPage = start, jumpTo = jumpTo),
            revision = it.favorites.subRevision + 1))) }
    }

    fun close() = host.update { if (!host.isActive) it else it.copy(favorites = it.favorites.copy(
        sub = it.favorites.sub.copy(opened = null))) }

    /** 列表已滚动到目标视频：之后不再重复定位，也允许向上补页。 */
    fun consumeJump() {
        val opened = host.current.favorites.opened ?: return
        updateOpened(opened.revision) { it.copy(jumpTo = null) }
    }

    suspend fun load() {
        val opened = host.current.favorites.opened ?: return
        if (opened.ready || busy()) return
        host.loading(0, true); host.error(0, null)
        val revision = opened.revision + 1
        updateOpened(opened.revision) { it.copy(revision = revision) }
        try {
            var start = opened.firstPage
            var result = page(opened.folder, start)
            // 定位页里找不到目标（UP 调整或删减了合集），退回第一页从头显示，不做定位。
            val target = opened.jumpTo
            if (start > 1 && target != null && result is Outcome.Success && result.value.first.none { it.bvid == target }) {
                start = 1
                result = page(opened.folder, 1)
            }
            when (result) {
                is Outcome.Success -> updateOpened(revision) { it.copy(
                    videos = result.value.first, page = start, firstPage = start, hasMore = result.value.second,
                    ready = true, jumpTo = if (start == opened.firstPage) it.jumpTo else null) }
                is Outcome.Failure -> {
                    if (revisionMatches(revision)) host.error(0, result.error.messageOr("收藏夹加载失败"))
                    updateOpened(revision) { it.copy(ready = true, jumpTo = null) }
                }
            }
        } finally { if (revisionMatches(revision)) host.loading(0, false) }
    }

    suspend fun loadMore() {
        val opened = host.current.favorites.opened ?: return
        if (!host.isActive || !opened.ready || !opened.hasMore || busy()) return
        host.loading(0, true)
        try {
            when (val result = page(opened.folder, opened.page + 1)) {
                is Outcome.Success -> updateOpened(opened.revision) { it.copy(
                    videos = (it.videos + result.value.first).distinctBy { video -> video.bvid },
                    page = opened.page + 1, hasMore = result.value.second) }
                is Outcome.Failure -> host.error(0, result.error.messageOr("加载更多失败"))
            }
        } finally { host.loading(0, false) }
    }

    /** 向上补一页；定位完成前不补，避免列表还没滚到目标就被插入的内容顶开。 */
    suspend fun loadPrevious() {
        val opened = host.current.favorites.opened ?: return
        if (!host.isActive || !opened.ready || !opened.hasPrevious || opened.jumpTo != null || busy()) return
        host.loading(0, true)
        try {
            when (val result = page(opened.folder, opened.firstPage - 1)) {
                is Outcome.Success -> updateOpened(opened.revision) { it.copy(
                    videos = (result.value.first + it.videos).distinctBy { video -> video.bvid },
                    firstPage = opened.firstPage - 1) }
                is Outcome.Failure -> host.error(0, result.error.messageOr("加载更早的视频失败"))
            }
        } finally { host.loading(0, false) }
    }

    private fun busy() = host.current.loading[0] == true || host.current.busy

    private suspend fun page(folder: FavoriteFolder, page: Int): Outcome<Pair<List<VideoItem>, Boolean>> = host.request {
        if (folder.type == 21) repository.seasonVideos(folder.id, page) else repository.videos(folder.id, page)
    }

    private fun revisionMatches(revision: Int) = host.current.favorites.opened?.revision == revision

    private fun updateOpened(revision: Int, transform: (OpenedFolderState) -> OpenedFolderState) = host.update {
        val opened = it.favorites.opened
        if (!host.isActive || opened == null || opened.revision != revision) it else
            it.copy(favorites = it.favorites.copy(sub = it.favorites.sub.copy(opened = transform(opened))))
    }
}
