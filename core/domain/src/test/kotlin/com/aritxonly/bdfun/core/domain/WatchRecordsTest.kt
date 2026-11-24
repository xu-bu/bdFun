package com.aritxonly.bdfun.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WatchRecordsTest {
    @Test fun openingForUnderThreeSecondsIsNotWatched() {
        assertNull(mergeWatchRecord(null, 2_999L, 600_000L, 1L))
        assertNotNull(mergeWatchRecord(null, 3_000L, 600_000L, 1L))
    }

    @Test fun nearTheEndCountsAsFinished() {
        assertTrue(mergeWatchRecord(null, 596_000L, 600_000L, 1L)!!.finished)   // 最后 5 秒内
        assertTrue(mergeWatchRecord(null, 570_000L, 600_000L, 1L)!!.finished)   // 95%
        assertEquals(false, mergeWatchRecord(null, 300_000L, 600_000L, 1L)!!.finished)
    }

    @Test fun finishedStaysFinishedWhenRewatchingFromStart() {
        val done = WatchRecord(600_000L, 600_000L, true, 1L)
        val rewatch = mergeWatchRecord(done, 20_000L, 600_000L, 2L)!!
        assertTrue(rewatch.finished)
        assertEquals(1f, rewatch.fraction)
        assertEquals(20_000L, rewatch.positionMs)
    }

    @Test fun smallMovesAreNotRewritten() {
        val old = WatchRecord(60_000L, 600_000L, false, 1L)
        assertNull(mergeWatchRecord(old, 64_000L, 600_000L, 2L))
        assertNotNull(mergeWatchRecord(old, 70_000L, 600_000L, 2L))
    }

    @Test fun unknownDurationKeepsPreviousDuration() {
        val old = WatchRecord(60_000L, 600_000L, false, 1L)
        assertEquals(600_000L, mergeWatchRecord(old, 120_000L, 0L, 2L)!!.durationMs)
    }

    @Test fun fractionIsPositionOverDuration() {
        assertEquals(0.25f, WatchRecord(150_000L, 600_000L, false, 1L).fraction)
        assertEquals(0f, WatchRecord(150_000L, 0L, false, 1L).fraction)
    }

    @Test fun furthestOnlyMovesForward() {
        val old = CollectionFurthest(10, "BV10", 1L)
        assertNull(mergeCollectionFurthest(old, 9, "BV9", 2L))
        assertNull(mergeCollectionFurthest(old, 10, "BV10", 2L))
        assertEquals(CollectionFurthest(11, "BV11", 2L), mergeCollectionFurthest(old, 11, "BV11", 2L))
        assertEquals(CollectionFurthest(0, "BV0", 2L), mergeCollectionFurthest(null, 0, "BV0", 2L))
        assertNull(mergeCollectionFurthest(null, -1, "BV0", 2L))
    }

    @Test fun pageOfIndexMatchesTwentyPerPage() {
        assertEquals(1, favoriteSeasonPageOf(0))
        assertEquals(1, favoriteSeasonPageOf(19))
        assertEquals(2, favoriteSeasonPageOf(20))
        assertEquals(43, favoriteSeasonPageOf(849))
    }

    @Test fun codecsRoundTripAndRejectGarbage() {
        val record = WatchRecord(1L, 2L, true, 3L)
        assertEquals(record, decodeWatchRecord(encodeWatchRecord(record)))
        assertNull(decodeWatchRecord("1,2"))
        assertNull(decodeWatchRecord("a,2,1,3"))
        val furthest = CollectionFurthest(7, "BV7", 9L)
        assertEquals(furthest, decodeCollectionFurthest(encodeCollectionFurthest(furthest)))
        assertNull(decodeCollectionFurthest("7,,9"))
    }

    @Test fun evictionDropsOldestDownToNinetyPercent() {
        val entries = (1..11).associate { "k$it" to it.toLong() }
        assertEquals(listOf("k1", "k2"), oldestBeyondLimit(entries, 10))
        assertTrue(oldestBeyondLimit(entries, 11).isEmpty())
    }
}
