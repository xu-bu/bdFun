package com.aritxonly.bdfun.infra.storage

import android.content.Context
import com.aritxonly.bdfun.core.domain.WatchRecordStorage

/** 独立 watch_records 文件；不写入旧 SettingsStore，也不改 playback_progress 的续播格式。 */
class AndroidWatchRecordStorage(context: Context) : WatchRecordStorage {
    private val prefs = context.applicationContext.getSharedPreferences("watch_records", Context.MODE_PRIVATE)
    override fun readAll(): Map<String, String> =
        prefs.all.mapNotNull { (key, value) -> (value as? String)?.let { key to it } }.toMap()
    override fun write(key: String, value: String) { prefs.edit().putString(key, value).apply() }
    override fun remove(keys: Collection<String>) {
        if (keys.isEmpty()) return
        val editor = prefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
    }
}
