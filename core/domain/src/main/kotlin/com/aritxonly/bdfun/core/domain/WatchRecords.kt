package com.aritxonly.bdfun.core.domain

import kotlin.math.abs

/**
 * 本机观看记录：与续播进度（[PlaybackProgressRepository]）分开保存。
 * 续播进度看完即清除、只留 500 条，无法回答"哪些视频看过"；这里看完也保留，
 * 用于所有视频封面的已看进度条，以及收藏页「合集」定位看过的最远一集。
 */
data class WatchRecord(val positionMs: Long, val durationMs: Long, val finished: Boolean, val updatedAt: Long) {
    /** 封面进度条比例：看完显示满条。 */
    val fraction: Float get() = when {
        finished -> 1f
        durationMs <= 0L -> 0f
        else -> (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }
}

/** 合集里看过的最远一集；index 为合集列表下标（0 起）。 */
data class CollectionFurthest(val index: Int, val bvid: String, val updatedAt: Long)

/** 原始键值存储；键为 [watchRecordKey] / [collectionFurthestKey]，值为本文件的 CSV 编码。 */
interface WatchRecordStorage {
    fun readAll(): Map<String, String>
    fun write(key: String, value: String)
    fun remove(keys: Collection<String>)
}

/** 收藏页「合集」列表 `/x/space/fav/season/list` 的每页条数；按下标换算页码时必须与请求一致。 */
const val FAVORITE_SEASON_PAGE_SIZE = 20
const val WATCH_RECORD_LIMIT = 3000
const val COLLECTION_FURTHEST_LIMIT = 500

private const val MIN_WATCHED_MS = 3000L
private const val FINISH_TAIL_MS = 5000L
private const val FINISH_RATIO = 0.95
private const val MIN_CHANGE_MS = 5000L

fun watchRecordKey(bvid: String) = "w_$bvid"
fun collectionFurthestKey(seasonId: Long) = "c_$seasonId"

/**
 * 合并一次播放进度；返回 null 表示无需写入。
 * 刚点开不足 3 秒不算看过；看完的标记一旦成立就保留（重看从头不会把封面变回未看）。
 */
fun mergeWatchRecord(old: WatchRecord?, positionMs: Long, durationMs: Long, now: Long): WatchRecord? {
    if (positionMs < 0L) return null
    if (old == null && positionMs < MIN_WATCHED_MS) return null
    val duration = if (durationMs > 0L) durationMs else old?.durationMs ?: 0L
    val finishedNow = duration > 0L && (positionMs >= duration - FINISH_TAIL_MS || positionMs >= duration * FINISH_RATIO)
    val next = WatchRecord(positionMs, duration, finishedNow || old?.finished == true, now)
    if (old != null && old.finished == next.finished && old.durationMs == next.durationMs &&
        abs(old.positionMs - positionMs) < MIN_CHANGE_MS) return null
    return next
}

/** 只向更远的一集推进；同一集不重复写入。 */
fun mergeCollectionFurthest(old: CollectionFurthest?, index: Int, bvid: String, now: Long): CollectionFurthest? {
    if (index < 0 || bvid.isBlank()) return null
    if (old != null && old.index >= index) return null
    return CollectionFurthest(index, bvid, now)
}

/** 合集下标所在的收藏页页码（1 起）。 */
fun favoriteSeasonPageOf(index: Int): Int = index.coerceAtLeast(0) / FAVORITE_SEASON_PAGE_SIZE + 1

fun encodeWatchRecord(record: WatchRecord) =
    "${record.positionMs},${record.durationMs},${if (record.finished) 1 else 0},${record.updatedAt}"

fun decodeWatchRecord(raw: String?): WatchRecord? {
    val parts = raw?.split(",") ?: return null
    if (parts.size < 4) return null
    val position = parts[0].toLongOrNull() ?: return null
    val duration = parts[1].toLongOrNull() ?: return null
    val updatedAt = parts[3].toLongOrNull() ?: return null
    return WatchRecord(position, duration, parts[2] == "1", updatedAt)
}

fun encodeCollectionFurthest(value: CollectionFurthest) = "${value.index},${value.bvid},${value.updatedAt}"

fun decodeCollectionFurthest(raw: String?): CollectionFurthest? {
    val parts = raw?.split(",") ?: return null
    if (parts.size < 3) return null
    val index = parts[0].toIntOrNull() ?: return null
    val updatedAt = parts[2].toLongOrNull() ?: return null
    return CollectionFurthest(index, parts[1].ifBlank { return null }, updatedAt)
}

/** 超过上限时按最近更新时间保留，删掉最旧的到上限的 90%，避免每次写入都触发清理。 */
fun <K> oldestBeyondLimit(updatedAt: Map<K, Long>, limit: Int): List<K> {
    if (updatedAt.size <= limit) return emptyList()
    val keep = limit * 9 / 10
    return updatedAt.entries.sortedBy { it.value }.take(updatedAt.size - keep).map { it.key }
}
