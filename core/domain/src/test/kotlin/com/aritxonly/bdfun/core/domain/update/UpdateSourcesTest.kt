package com.aritxonly.bdfun.core.domain.update

import org.junit.Assert.*
import org.junit.Test

class UpdateSourcesTest {
    @Test fun releasesApiPointsAtThisRepository() {
        assertEquals("https://api.github.com/repos/xu-bu/bdFun/releases", RELEASES_API_URL)
    }

    @Test fun bothCurrentAndLegacyDownloadUrlsAreAccepted() {
        assertTrue(isReleaseDownloadUrl("https://github.com/xu-bu/bdFun/releases/download/vpreview-7.9/bdFun.apk"))
        assertTrue(isReleaseDownloadUrl("https://github.com/QxTeak/bdFun/releases/download/preview-5.0/bdFun.apk"))
        assertFalse(isReleaseDownloadUrl("https://example.com/bdFun.apk"))
        assertFalse(isReleaseDownloadUrl("https://github.com/xu-bu/bdFun/releases/download/vpreview-7.9/mapping.txt"))
    }
}
