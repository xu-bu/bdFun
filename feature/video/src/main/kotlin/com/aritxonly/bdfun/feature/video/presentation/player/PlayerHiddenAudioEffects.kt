package com.aritxonly.bdfun.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.aritxonly.bdfun.core.domain.shouldUseAudioOnlyWhileHidden
import com.aritxonly.bdfun.feature.video.presentation.VideoPlaybackPlatform

/**
 * 锁屏/回桌面时自动「听视频」：页面不可见且继续后台播放时关掉视频轨，只下载和解码音频；
 * 回到页面再打开视频轨（播放器会先补一小段画面缓冲）。与手动「听视频」用同一个开关，
 * 但不改 fsAudioOnly，回来后界面保持原样。画中画小窗可见，不会触发 ON_STOP。
 */
@Composable
fun BindPlayerHiddenAudioOnly(playback: VideoPlaybackPlatform, ui: VideoPlayerUiStateHolder, backgroundPlay: () -> Boolean) {
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val currentBackgroundPlay by rememberUpdatedState(backgroundPlay)
    DisposableEffect(lifecycleOwner, playback) {
        var videoOffWhileHidden = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> if (shouldUseAudioOnlyWhileHidden(currentBackgroundPlay(), ui.fsAudioOnly.value)) {
                    videoOffWhileHidden = true
                    playback.setVideoEnabled(false)
                }
                Lifecycle.Event.ON_START -> if (videoOffWhileHidden) {
                    videoOffWhileHidden = false
                    playback.setVideoEnabled(!ui.fsAudioOnly.value)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        // 页面关闭（交给唱片继续播放）时不恢复画面：后台播放器本来就看不到画面，继续只加载音频。
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
}
