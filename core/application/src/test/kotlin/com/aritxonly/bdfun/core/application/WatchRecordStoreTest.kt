package com.aritxonly.bdfun.core.application

import com.aritxonly.bdfun.core.domain.CollectionFurthest
import com.aritxonly.bdfun.core.domain.PlaybackProgressRepository
import com.aritxonly.bdfun.core.domain.WatchRecordStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchRecordStoreTest {
    private class MemoryStorage : WatchRecordStorage {
        val values = LinkedHashMap<String, String>()
        override fun readAll() = values.toMap()
        override fun write(key: String, value: String) { values[key] = value }
        override fun remove(keys: Collection<String>) { keys.forEach { values.remove(it) } }
    }

    @Test fun recordsSurviveRestart() {
        val storage = MemoryStorage()
        WatchRecordStore(storage) { 1L }.record("BV1", 60_000L, 600_000L)
        val restored = WatchRecordStore(storage) { 2L }
        assertEquals(60_000L, restored.records.value["BV1"]?.positionMs)
    }

    @Test fun watchingAnEpisodeAdvancesItsCollectionFurthest() {
        val storage = MemoryStorage()
        val store = WatchRecordStore(storage) { 1L }
        store.locate(77L, listOf("BV0", "BV1", "BV2"), firstIndex = 840)
        store.record("BV1", 60_000L, 600_000L)
        assertEquals(CollectionFurthest(841, "BV1", 1L), store.furthest(77L))
        store.record("BV0", 60_000L, 600_000L)
        assertEquals(841, store.furthest(77L)?.index)
        assertEquals(841, WatchRecordStore(storage) { 2L }.furthest(77L)?.index)
    }

    @Test fun locatingAlreadyWatchedEpisodesCountsThem() {
        val store = WatchRecordStore(MemoryStorage()) { 1L }
        store.record("BV9", 60_000L, 600_000L)
        assertNull(store.furthest(5L))
        store.locate(5L, listOf("BV8", "BV9"), firstIndex = 20)
        assertEquals(21, store.furthest(5L)?.index)
    }

    @Test fun unknownCollectionAndBlankVideoAreIgnored() {
        val store = WatchRecordStore(MemoryStorage()) { 1L }
        store.locate(0L, listOf("BV1"))
        store.record("", 60_000L, 600_000L)
        store.record("BV1", 60_000L, 600_000L)
        assertTrue(store.records.value.keys == setOf("BV1"))
        assertNull(store.furthest(0L))
    }

    @Test fun wrapperKeepsResumeBehaviourAndForwardsWatching() {
        val saved = mutableListOf<String>()
        val inner = object : PlaybackProgressRepository {
            override fun resumeFor(bvid: String) = 42L
            override fun save(bvid: String, positionMs: Long, durationMs: Long) { saved += bvid }
            override fun clear(bvid: String) {}
        }
        val store = WatchRecordStore(MemoryStorage()) { 1L }
        val wrapped = WatchRecordingProgressRepository(inner, store)
        wrapped.save("BV1", 3L, 60_000L, 600_000L)
        assertEquals(listOf("BV1_3"), saved)
        assertEquals(42L, wrapped.resumeFor("BV1", 3L))
        wrapped.recordWatch("BV1", 3L, 60_000L, 600_000L)
        assertEquals(60_000L, store.records.value["BV1"]?.positionMs)
    }
}
