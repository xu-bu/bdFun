package com.aritxonly.bdfun.core.domain

import com.aritxonly.bdfun.core.model.library.FavoriteFolder
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentFolderOrderTest {
    private fun folder(id: Long, type: Int = 21) = FavoriteFolder(id, "f$id", 1, "", type = type)
    private fun ids(list: List<FavoriteFolder>) = list.map { it.id }

    @Test fun recentlyOpenedComeFirstNewestFirst() {
        val folders = listOf(folder(1), folder(2), folder(3), folder(4))
        val opened = mapOf("21:3" to 100L, "21:1" to 300L)
        assertEquals(listOf(1L, 3L, 2L, 4L), ids(orderByRecentOpen(folders, opened)))
    }

    @Test fun unopenedKeepApiOrder() {
        val folders = listOf(folder(5), folder(2), folder(9))
        assertEquals(listOf(5L, 2L, 9L), ids(orderByRecentOpen(folders, emptyMap())))
        assertEquals(listOf(9L, 5L, 2L), ids(orderByRecentOpen(folders, mapOf("21:9" to 1L))))
    }

    @Test fun typeIsPartOfTheKey() {
        val folders = listOf(folder(7, type = 11), folder(7, type = 21))
        assertEquals(listOf(21, 11), orderByRecentOpen(folders, mapOf("21:7" to 1L)).map { it.type })
    }
}
