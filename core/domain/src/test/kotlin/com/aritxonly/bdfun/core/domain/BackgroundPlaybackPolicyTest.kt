package com.aritxonly.bdfun.core.domain

import org.junit.Assert.*
import org.junit.Test

class BackgroundPlaybackPolicyTest {
    @Test fun invisiblePageDoesNotTriggerExitPreference() {
        // Home、锁屏、画中画被遮住均只改变可见性，页面没有 finish。
        assertFalse(shouldPauseForVideoPageExit(true, false, false))
        assertTrue(shouldPauseForVideoPageExit(true, true, false))
        assertFalse(shouldPauseForVideoPageExit(false, true, false))
        assertFalse(shouldPauseForVideoPageExit(true, true, true))
    }

    @Test fun playingOrBufferingWithPlaybackIntentNeedsService() {
        assertTrue(shouldKeepPlaybackAlive(true, true, true))
    }

    @Test fun userPauseDisabledBackgroundAndMissingSessionDoNotStartService() {
        assertFalse(shouldKeepPlaybackAlive(true, true, false))
        assertFalse(shouldKeepPlaybackAlive(false, true, true))
        assertFalse(shouldKeepPlaybackAlive(true, false, true))
    }

    @Test fun pausedVideoDoesNotEnterPipOnExit() {
        // 设置允许画中画，但用户主动暂停后退出不应进小窗。
        assertFalse(shouldEnterPipOnExit(true, true, false))
        assertTrue(shouldEnterPipOnExit(true, true, true))
        assertFalse(shouldEnterPipOnExit(false, true, true))
        assertFalse(shouldEnterPipOnExit(true, false, true))
    }

    @Test fun loadingVideoEntersPipWhenItWillAutoPlay() {
        // 详情/播放地址还在加载时播放器空闲、playWhenReady 为默认 false，不能当成用户暂停。
        assertTrue(shouldEnterPipOnExit(true, true, false, stillLoading = true, autoPlay = true))
        assertFalse(shouldEnterPipOnExit(true, true, false, stillLoading = true, autoPlay = false))
        assertFalse(shouldEnterPipOnExit(true, true, false, stillLoading = false, autoPlay = true))
        assertFalse(shouldEnterPipOnExit(true, false, false, stillLoading = true, autoPlay = true))
    }
}
