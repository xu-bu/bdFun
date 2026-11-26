package com.aritxonly.bdfun.infra.storage

import android.content.Context
import com.aritxonly.bdfun.core.domain.RecentFolderStorage

/** 独立 favorite_recent 文件：键为「类型:id」，值为最后点开的毫秒时间戳。 */
class AndroidRecentFolderStorage(context: Context) : RecentFolderStorage {
    private val prefs = context.applicationContext.getSharedPreferences("favorite_recent", Context.MODE_PRIVATE)
    override fun readAll(): Map<String, Long> =
        prefs.all.mapNotNull { (key, value) -> (value as? Long)?.let { key to it } }.toMap()
    override fun write(key: String, openedAt: Long) { prefs.edit().putLong(key, openedAt).apply() }
    override fun remove(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
    }
}
