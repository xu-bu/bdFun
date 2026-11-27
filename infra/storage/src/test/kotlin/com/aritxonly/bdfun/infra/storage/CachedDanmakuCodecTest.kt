package com.aritxonly.bdfun.infra.storage

import com.aritxonly.bdfun.core.model.video.DanmakuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CachedDanmakuCodecTest {
    @Test fun roundTripKeepsEveryStoredField() {
        val items = listOf(
            DanmakuItem(1_234L, 1, 0xFF6699, 25f, "前排\"引号\"😀", id = 99L, likeCount = 7L, count = 3,
                userHash = "ab12", vipColor = true, upIdentity = true),
            DanmakuItem(65_000L, 5, 0xFFFFFF, 18f, "顶部弹幕"),
        )
        assertEquals(items, decodeCachedDanmaku(encodeCachedDanmaku(items)))
    }

    @Test fun selfMarkIsSessionOnlyAndNotStored() {
        val decoded = decodeCachedDanmaku(encodeCachedDanmaku(listOf(DanmakuItem(1L, 1, 0, 25f, "我发的", self = true))))
        assertEquals(false, decoded!!.single().self)
    }

    @Test fun malformedFileFallsBackToNull() {
        assertNull(decodeCachedDanmaku("not json"))
        assertNull(decodeCachedDanmaku("{\"version\":1}"))
    }
}
