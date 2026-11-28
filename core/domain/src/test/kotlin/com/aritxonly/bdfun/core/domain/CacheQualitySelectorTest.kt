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

    @Test fun picksHighestQualityRegardlessOfPlayback() {
        // qualities 由 PlayInfoMapper 按 qn 降序产出；缓存固定取最高档，不看当前播放清晰度。
        val highest = quality(120)
        assertEquals(highest, selectCacheQuality(info(highest, quality(80), quality(64))))
    }

    @Test fun skipsHighestWithBlankUrlAndTakesNext() {
        val next = quality(80)
        assertEquals(next, selectCacheQuality(info(quality(120, ""), next, quality(64))))
    }

    @Test fun allBlankUsesHighestWithFallbackUrl() {
        val highest = quality(120, "")
        val selected = selectCacheQuality(info(highest, quality(80, ""), fallback = "fallback-url"))
        assertEquals(highest.id, selected?.id)
        assertEquals("fallback-url", selected?.videoUrl)
    }

    @Test fun noUsableUrlOrQualityReturnsNull() {
        assertNull(selectCacheQuality(info()))
        assertNull(selectCacheQuality(info(quality(64, ""))))
    }
}
