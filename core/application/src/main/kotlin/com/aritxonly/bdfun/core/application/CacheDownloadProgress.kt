package com.aritxonly.bdfun.core.application

import com.aritxonly.bdfun.core.model.cache.*

/** 视频/音频共用发布与落盘时钟，原阶段上限和未知总长规则保持不变。 */
internal class CacheDownloadProgress(initial: CachedVideo, private val knownTotal: Long, private val now: () -> Long,
    videoSeed: Long = 0L, audioSeed: Long = 0L) {
    var current = run {
        val seeded = videoSeed + audioSeed
        val total = if (knownTotal > 0L) knownTotal else seeded
        initial.copy(transfer = initial.transfer.copy(bytes = seeded, totalBytes = total,
            progress = if (total > 0L) seeded.toFloat() / total else 0f))
    }
        private set
    private var videoDone = videoSeed
    private var audioDone = audioSeed
    private var lastPublished = 0L
    private var lastPersisted = 0L
    private var sampleBytes = videoSeed + audioSeed
    private var sampleTime = 0L
    var persist = false
        private set
    fun update(done: Long, audio: Boolean): CachedVideo? {
        if (audio) audioDone = done else videoDone = done
        val time = now()
        val bytes = videoDone + audioDone
        // 速度采样不受 220ms UI 节流影响；EMA 平滑掉分片抖动。
        if (sampleTime > 0L) {
            val elapsed = time - sampleTime
            val delta = bytes - sampleBytes
            if (elapsed > 0L && delta > 0L) {
                val instant = delta * 1000L / elapsed
                val smoothed = if (current.speedBps > 0L) (current.speedBps * 3 + instant) / 4 else instant
                current = current.copy(transfer = current.transfer.copy(speedBps = smoothed))
            }
        }
        sampleBytes = bytes
        sampleTime = time
        val total = if (knownTotal > 0L) knownTotal else bytes
        current = current.copy(transfer = current.transfer.copy(bytes = bytes, totalBytes = total,
            progress = if (total > 0L) (bytes.toFloat() / total).coerceIn(0f, if (audio) 0.99f else 0.98f) else 0f))
        if (time - lastPublished < 220L) return null
        lastPublished = time
        persist = time - lastPersisted >= 5000L
        if (persist) lastPersisted = time
        return current
    }
}
