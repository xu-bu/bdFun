package com.aritxonly.bdfun.core.application

import com.aritxonly.bdfun.core.domain.COLLECTION_FURTHEST_LIMIT
import com.aritxonly.bdfun.core.domain.CollectionFurthest
import com.aritxonly.bdfun.core.domain.PlaybackProgressRepository
import com.aritxonly.bdfun.core.domain.WATCH_RECORD_LIMIT
import com.aritxonly.bdfun.core.domain.WatchRecord
import com.aritxonly.bdfun.core.domain.WatchRecordStorage
import com.aritxonly.bdfun.core.domain.collectionFurthestKey
import com.aritxonly.bdfun.core.domain.decodeCollectionFurthest
import com.aritxonly.bdfun.core.domain.decodeWatchRecord
import com.aritxonly.bdfun.core.domain.encodeCollectionFurthest
import com.aritxonly.bdfun.core.domain.encodeWatchRecord
import com.aritxonly.bdfun.core.domain.mergeCollectionFurthest
import com.aritxonly.bdfun.core.domain.mergeWatchRecord
import com.aritxonly.bdfun.core.domain.oldestBeyondLimit
import com.aritxonly.bdfun.core.domain.watchRecordKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 进程内唯一的观看记录：内存 StateFlow 供封面订阅，写入同步落到 [WatchRecordStorage]。
 * 合集单集位置只在内存里（视频页 / 收藏页「合集」打开时登记），观看某集时据此推进该合集看过的最远一集。
 */
class WatchRecordStore(private val storage: WatchRecordStorage?, private val clock: () -> Long = System::currentTimeMillis) {
    private val lock = Any()
    private val recordsFlow = MutableStateFlow<Map<String, WatchRecord>>(emptyMap())
    private val furthest = HashMap<Long, CollectionFurthest>()
    private val positions = HashMap<String, Pair<Long, Int>>()
    private var writesSincePrune = 0

    val records: StateFlow<Map<String, WatchRecord>> = recordsFlow.asStateFlow()

    init {
        val raw = storage?.readAll().orEmpty()
        val loaded = HashMap<String, WatchRecord>()
        raw.forEach { (key, value) ->
            when {
                key.startsWith("w_") -> decodeWatchRecord(value)?.let { loaded[key.removePrefix("w_")] = it }
                key.startsWith("c_") -> key.removePrefix("c_").toLongOrNull()?.let { id ->
                    decodeCollectionFurthest(value)?.let { furthest[id] = it }
                }
            }
        }
        recordsFlow.value = loaded
    }

    fun record(bvid: String, positionMs: Long, durationMs: Long) {
        if (bvid.isBlank()) return
        synchronized(lock) {
            val next = mergeWatchRecord(recordsFlow.value[bvid], positionMs, durationMs, clock()) ?: return@synchronized
            recordsFlow.value = recordsFlow.value + (bvid to next)
            storage?.write(watchRecordKey(bvid), encodeWatchRecord(next))
            positions[bvid]?.let { (season, index) -> advance(season, index, bvid) }
            if (++writesSincePrune >= 100) { writesSincePrune = 0; prune() }
        }
    }

    /** 登记合集单集顺序；已看过的单集立刻参与最远一集的计算（例如在收藏页翻到了以前看过的集）。 */
    fun locate(seasonId: Long, bvids: List<String>, firstIndex: Int = 0) {
        if (seasonId <= 0L) return
        synchronized(lock) {
            val records = recordsFlow.value
            bvids.forEachIndexed { offset, bvid ->
                if (bvid.isBlank()) return@forEachIndexed
                val index = firstIndex + offset
                positions[bvid] = seasonId to index
                if (records.containsKey(bvid)) advance(seasonId, index, bvid)
            }
        }
    }

    fun furthest(seasonId: Long): CollectionFurthest? = synchronized(lock) { furthest[seasonId] }

    private fun advance(seasonId: Long, index: Int, bvid: String) {
        val next = mergeCollectionFurthest(furthest[seasonId], index, bvid, clock()) ?: return
        furthest[seasonId] = next
        storage?.write(collectionFurthestKey(seasonId), encodeCollectionFurthest(next))
    }

    private fun prune() {
        val staleRecords = oldestBeyondLimit(recordsFlow.value.mapValues { it.value.updatedAt }, WATCH_RECORD_LIMIT)
        val staleSeasons = oldestBeyondLimit(furthest.mapValues { it.value.updatedAt }, COLLECTION_FURTHEST_LIMIT)
        if (staleRecords.isEmpty() && staleSeasons.isEmpty()) return
        recordsFlow.value = recordsFlow.value - staleRecords.toSet()
        staleSeasons.forEach { furthest.remove(it) }
        storage?.remove(staleRecords.map(::watchRecordKey) + staleSeasons.map(::collectionFurthestKey))
    }
}

/** 包装原续播仓储：续播读写保持原样，只把观看记录与合集位置交给 [WatchRecordStore]。 */
class WatchRecordingProgressRepository(
    private val inner: PlaybackProgressRepository, private val store: WatchRecordStore,
) : PlaybackProgressRepository by inner {
    override fun recordWatch(bvid: String, cid: Long, positionMs: Long, durationMs: Long) =
        store.record(bvid, positionMs, durationMs)
    override fun locateCollection(seasonId: Long, bvids: List<String>, firstIndex: Int) =
        store.locate(seasonId, bvids, firstIndex)
}
