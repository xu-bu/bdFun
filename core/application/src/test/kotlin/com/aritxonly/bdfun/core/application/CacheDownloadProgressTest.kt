package com.aritxonly.bdfun.core.application

import org.junit.Assert.*
import org.junit.Test

class CacheDownloadProgressTest {
    @Test fun publishesAt220msAndSharesClockAcrossBothTracks() {
        var now = 219L; val progress = CacheDownloadProgress(cacheEntry(), 1000, { now })
        assertNull(progress.update(100, false)); now = 220
        assertEquals(0.2f, progress.update(200, false)!!.progress)
        now = 330; assertNull(progress.update(300, false))
        now = 440; val audio = progress.update(50, true)!!
        assertEquals(350L, audio.bytes); assertEquals(0.35f, audio.progress)
        assertEquals(1000L, audio.totalBytes)
    }
    @Test fun unknownTotalUsesAccumulatedBytesWithOriginalPhaseCaps() {
        var now = 220L; val progress = CacheDownloadProgress(cacheEntry(), 0, { now })
        val video = progress.update(100, false)!!
        assertEquals(100L, video.totalBytes); assertEquals(0.98f, video.progress)
        now = 440; val audio = progress.update(20, true)!!
        assertEquals(120L, audio.totalBytes); assertEquals(0.99f, audio.progress)
    }
    @Test fun persistenceOnlyChecksOnPublishedSamplesAndEvery5000ms() {
        var now = 4999L; val progress = CacheDownloadProgress(cacheEntry(), 1000, { now })
        progress.update(10, false); assertFalse(progress.persist)
        now = 5000; assertNull(progress.update(20, false))
        now = 5219; progress.update(30, false); assertTrue(progress.persist)
        now = 5439; progress.update(40, false); assertFalse(progress.persist)
        now = 10219; progress.update(50, true); assertTrue(progress.persist)
    }
    @Test fun seededProgressStartsAtResumedBytesAndSpeedIsSmoothed() {
        var now = 1000L
        val progress = CacheDownloadProgress(cacheEntry(), 1000, { now }, videoSeed = 400L)
        assertEquals(400L, progress.current.bytes)
        assertEquals(0.4f, progress.current.progress)
        now = 2000; val first = progress.update(500, false)!!
        assertEquals(0L, first.speedBps) // 首次采样只建立基线
        now = 3000; val second = progress.update(800, false)!!
        assertEquals(300L, second.speedBps)
        now = 4000; val third = progress.update(1100, false)!!
        assertEquals(300L, third.speedBps)
    }
    @Test fun speedContinuesSamplingDuringUiThrottle() {
        var now = 1000L
        val progress = CacheDownloadProgress(cacheEntry(), 100_000, { now })
        progress.update(1000, false)
        now = 1100; assertNull(progress.update(2000, false))
        now = 1300; val published = progress.update(3000, false)!!
        assertEquals(8750L, published.speedBps)
        assertEquals(3000L, published.bytes)
    }
    @Test fun zeroOrOverrunByteCountsKeepOriginalBounds() {
        var now = 220L; val progress = CacheDownloadProgress(cacheEntry(), 0, { now })
        assertEquals(0f, progress.update(0, false)!!.progress)
        now = 440; assertEquals(0.98f, progress.update(100, false)!!.progress)
        val known = CacheDownloadProgress(cacheEntry(), 5, { 10000 })
        assertEquals(0.99f, known.update(200, true)!!.progress)
    }
}
