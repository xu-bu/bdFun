package com.aritxonly.bdfun.core.application

import com.aritxonly.bdfun.core.domain.RECENT_FOLDER_LIMIT
import com.aritxonly.bdfun.core.domain.RecentFolderStorage
import com.aritxonly.bdfun.core.domain.oldestBeyondLimit
import com.aritxonly.bdfun.core.domain.recentFolderKey
import com.aritxonly.bdfun.core.model.library.FavoriteFolder
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 收藏/合集最后点开时间：内存 StateFlow 供列表排序订阅，写入同步落盘。 */
class RecentFolderStore(private val storage: RecentFolderStorage?, private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private val mutable = MutableStateFlow(storage?.readAll().orEmpty())
    val openedAt: StateFlow<Map<String, Long>> = mutable.asStateFlow()

    /** 当前快照；界面订阅前用作初值，持续更新走 [openedAt]。 */
    fun snapshot(): Map<String, Long> = mutable.value

    fun markOpened(folder: FavoriteFolder) {
        if (folder.id <= 0L) return
        synchronized(lock) {
            val key = recentFolderKey(folder)
            val now = clock()
            var next = mutable.value + (key to now)
            storage?.write(key, now)
            val stale = oldestBeyondLimit(next, RECENT_FOLDER_LIMIT)
            if (stale.isNotEmpty()) { next = next - stale.toSet(); storage?.remove(stale) }
            mutable.value = next
        }
    }
}
