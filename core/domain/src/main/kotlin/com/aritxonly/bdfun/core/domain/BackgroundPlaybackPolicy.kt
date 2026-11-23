package com.aritxonly.bdfun.core.domain

/** 缓冲时 isPlaying=false，但用户仍希望续播，必须按播放意图保活。 */
fun shouldKeepPlaybackAlive(backgroundEnabled: Boolean, active: Boolean, playWhenReady: Boolean): Boolean =
    backgroundEnabled && active && playWhenReady

/** 页面退出偏好只约束真正关闭页面，不能把失去可见性当作退出。 */
fun shouldPauseForVideoPageExit(stopOnExit: Boolean, finishing: Boolean, changingConfiguration: Boolean): Boolean =
    stopOnExit && finishing && !changingConfiguration

/**
 * 退出视频页是否进入画中画：仅在设置允许、且用户仍有播放意图时进入。
 * 用 playWhenReady 而非 isPlaying——缓冲时 isPlaying=false 但用户并未暂停，仍应进画中画续播。
 */
fun shouldEnterPipOnExit(showDiscAfterVideo: Boolean, exitControlIsPip: Boolean, playWhenReady: Boolean): Boolean =
    showDiscAfterVideo && exitControlIsPip && playWhenReady
