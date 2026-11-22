package com.aritxonly.bdfun.core.domain

import com.aritxonly.bdfun.core.model.video.PlayInfo
import com.aritxonly.bdfun.core.model.video.Quality
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CacheQualitySelectorTest {
    private fun quality(id: Int, url: String = "video-$id") = Quality(id, "${id}P", 0, 0, "", "", url)
    private fun info(vararg qualities: Quality, fallback: String? = null) = PlayInfo(
        qualities = qualities.toList(),
        audioUrl = "audio",
        fallbackUrl = fallback,
        durationMs = 1000L,
        acceptQuality = qualities.map { it.id },
    )

    @Test fun preferredQualityWins() {
        val first = quality(80)
        val preferred = quality(64)
        assertEquals(preferred, selectCacheQuality(info(first, preferred), 64))
    }

    @Test fun fallsBackTo720pThenLastQuality() {
        val last = quality(32)
        val p720 = quality(64)
        assertEquals(p720, selectCacheQuality(info(last, p720), 999))
        assertEquals(last, selectCacheQuality(info(last), 999))
    }

    @Test fun blankVideoUrlUsesFallback() {
        val selected = selectCacheQuality(info(quality(64, ""), fallback = "fallback-url"), 64)
        assertEquals("fallback-url", selected?.videoUrl)
    }

    @Test fun noUsableUrlOrQualityReturnsNull() {
        assertNull(selectCacheQuality(info(), 64))
        assertNull(selectCacheQuality(info(quality(64, "")), 64))
    }
}
