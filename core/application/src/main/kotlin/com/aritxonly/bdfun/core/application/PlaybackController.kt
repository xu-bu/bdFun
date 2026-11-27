package com.aritxonly.bdfun.core.application

import kotlinx.coroutines.flow.StateFlow

enum class PlaybackPhase { Idle, Buffering, Ready, Ended }
data class PlaybackFailure(val code: String, val message: String?)
data class PlaybackSnapshot(
    val playing: Boolean = false, val playWhenReady: Boolean = false,
    val phase: PlaybackPhase = PlaybackPhase.Idle, val ready: Boolean = false, val ended: Boolean = false,
    val positionMs: Long = 0, val bufferedMs: Long = 0, val durationMs: Long = 0,
    val speed: Float = 1f, val error: PlaybackFailure? = null,
)
data class PlaybackSource(
    val videoUrl: String, val audioUrl: String? = null,
    val headers: Map<String, String> = emptyMap(), val userAgent: String,
    val resumePositionMs: Long = 0, val autoPlay: Boolean = true, val speed: Float = 1f,
    val mediaKey: String? = null,
)

/** 前台、后台和系统媒体控件共享传输控制，不暴露 Media3 或 Android 类型。 */
interface PlaybackController {
    val state: StateFlow<PlaybackSnapshot>
    fun refresh(): PlaybackSnapshot
    /**
     * 只读当前播放位置：不采样缓冲统计、不发布状态。
     * 给弹幕时钟这类每帧读取用；refresh() 每次都会发布新位置，界面里读位置的地方会跟着整块重组。
     */
    fun currentPositionMs(): Long = state.value.positionMs
    fun prepare(source: PlaybackSource)
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun setSpeed(speed: Float)
    /** 投屏时把手机播放器静音；默认空实现，避免影响测试替身与其它实现。 */
    fun setMuted(muted: Boolean) {}
    fun acknowledgeEnd()
    fun release()
    fun addListener(listener: Listener)
    fun removeListener(listener: Listener)

    interface Listener {
        fun onRenderedFirstFrame() {}
        fun onPlayingChanged(playing: Boolean) {}
        fun onPhaseChanged(phase: PlaybackPhase) {}
        /** 播放器发生 seek（进度条拖动/手势/系统控件），投屏借此把进度同步到电视。 */
        fun onSeek(positionMs: Long) {}
        fun onError(error: PlaybackFailure) {}
    }
}
