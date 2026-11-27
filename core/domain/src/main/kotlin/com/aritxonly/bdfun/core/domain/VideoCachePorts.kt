package com.aritxonly.bdfun.core.domain

import com.aritxonly.bdfun.core.common.Outcome
import com.aritxonly.bdfun.core.model.cache.*
import com.aritxonly.bdfun.core.model.video.DanmakuItem

interface VideoCacheStorage {
    /** 缓存时一并保存的弹幕（离线播放用）；条目目录已被删除时不得重新创建。默认不保存。 */
    fun writeDanmaku(id: String, items: List<DanmakuItem>) {}
    /** 没有弹幕文件（旧缓存或保存失败）返回 null，调用方再联网加载。 */
    fun readDanmaku(id: String): List<DanmakuItem>? = null
    fun read(): List<CachedVideo>
    fun write(entries: List<CachedVideo>)
    fun prepare(id: String): CacheDestination
    fun delete(id: String)
    fun exists(path: String): Boolean
    fun uri(path: String): String
    fun commit(part: String, target: String)
    fun size(path: String): Long
    fun removePartial(destination: CacheDestination)
}
interface VideoCacheTransfer {
    suspend fun contentLength(url: String): Long
    suspend fun download(url: String, target: String, onBytes: suspend (Long) -> Unit): Outcome<Long>
    /**
     * 从 [resumeFrom] 字节处续传；[onBytes] 返回的是「包含已续接前缀」的累计字节数。
     * 默认回退到从头下载（不感知 Range 的实现无需改动）。
     */
    suspend fun download(url: String, target: String, resumeFrom: Long,
        onBytes: suspend (Long) -> Unit): Outcome<Long> = download(url, target, onBytes)
    suspend fun cover(url: String, target: String): String
}
