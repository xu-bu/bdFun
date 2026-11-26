package com.aritxonly.bdfun.core.domain

import com.aritxonly.bdfun.core.model.library.FavoriteFolder

/**
 * 收藏页「我的收藏与订阅」按最近点开排序。接口只按收藏时间返回，没有"最近打开"，
 * 所以由本机记录每个收藏/合集最后一次点开的时间。
 */
interface RecentFolderStorage {
    fun readAll(): Map<String, Long>
    fun write(key: String, openedAt: Long)
    fun remove(keys: Collection<String>)
}

const val RECENT_FOLDER_LIMIT = 300

/** 合集（type 21）与普通收藏夹的 id 来自不同号段，键里带上类型避免撞号。 */
fun recentFolderKey(folder: FavoriteFolder) = "${folder.type}:${folder.id}"

/** 点开过的按时间倒序排在前面；没点开过的保持接口原顺序跟在后面。 */
fun orderByRecentOpen(folders: List<FavoriteFolder>, openedAt: Map<String, Long>): List<FavoriteFolder> {
    if (openedAt.isEmpty()) return folders
    val (opened, rest) = folders.partition { openedAt.containsKey(recentFolderKey(it)) }
    return opened.sortedByDescending { openedAt.getValue(recentFolderKey(it)) } + rest
}
