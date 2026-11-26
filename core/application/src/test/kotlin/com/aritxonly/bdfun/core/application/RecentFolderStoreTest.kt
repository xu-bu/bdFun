package com.aritxonly.bdfun.core.application

import com.aritxonly.bdfun.core.domain.RECENT_FOLDER_LIMIT
import com.aritxonly.bdfun.core.domain.RecentFolderStorage
import com.aritxonly.bdfun.core.model.library.FavoriteFolder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecentFolderStoreTest {
    private class MemoryStorage : RecentFolderStorage {
        val values = LinkedHashMap<String, Long>()
        override fun readAll() = values.toMap()
        override fun write(key: String, openedAt: Long) { values[key] = openedAt }
        override fun remove(keys: Collection<String>) { keys.forEach { values.remove(it) } }
    }

    @Test fun openingRecordsTimeAndSurvivesRestart() {
        val storage = MemoryStorage(); var now = 10L
        val store = RecentFolderStore(storage) { now }
        store.markOpened(FavoriteFolder(5, "合集", 1, "", type = 21))
        now = 20L
        store.markOpened(FavoriteFolder(5, "合集", 1, "", type = 21))
        assertEquals(mapOf("21:5" to 20L), store.openedAt.value)
        assertEquals(mapOf("21:5" to 20L), RecentFolderStore(storage).snapshot())
    }

    @Test fun invalidFolderIsIgnoredAndOldEntriesArePruned() {
        val storage = MemoryStorage(); var now = 0L
        val store = RecentFolderStore(storage) { now }
        store.markOpened(FavoriteFolder(0, "无效", 1, ""))
        assertTrue(store.snapshot().isEmpty())
        repeat(RECENT_FOLDER_LIMIT + 1) { now = it.toLong(); store.markOpened(FavoriteFolder(it + 1L, "f", 1, "", type = 21)) }
        assertEquals(RECENT_FOLDER_LIMIT * 9 / 10, store.snapshot().size)
        assertEquals(store.snapshot(), storage.values.toMap())
    }
}
