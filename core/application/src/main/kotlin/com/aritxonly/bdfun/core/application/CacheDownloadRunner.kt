package com.aritxonly.bdfun.core.application

import com.aritxonly.bdfun.core.common.*
import com.aritxonly.bdfun.core.domain.*
import com.aritxonly.bdfun.core.model.cache.*
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

internal class CacheDownloadRunner(private val storage: VideoCacheStorage, private val transfer: VideoCacheTransfer,
    private val now: () -> Long) {
    suspend fun run(request: CacheDownloadRequest, current: () -> Boolean, cleanupAllowed: () -> Boolean,
        publish: suspend (CachedVideo) -> Unit, persist: suspend () -> Unit) {
        checkCurrent(current)
        var entry = request.initial.copy(transfer = request.initial.transfer.copy(status = CacheStatus.Downloading))
        publish(entry)
        val destination = request.destination
        var progress: CacheDownloadProgress? = null
        try {
            val videoLength = transfer.contentLength(request.videoUrl)
            checkCurrent(current)
            val audioLength = transfer.contentLength(request.audioUrl)
            val total = (videoLength.coerceAtLeast(0L) + audioLength.coerceAtLeast(0L)).takeIf { it > 0L } ?: 0L
            // 续传：已完成轨直接跳过；部分完成的 .part 保留为下一次 Range 起点。
            val video = readyTrack(destination.videoPart, destination.videoFile, videoLength)
            val audio = readyTrack(destination.audioPart, destination.audioFile, audioLength)
            val activeProgress = CacheDownloadProgress(entry, total, now, video.first, audio.first)
            progress = activeProgress
            if (!video.second) {
                downloadTrack(request.videoUrl, destination.videoPart, video.first, false, activeProgress, current, publish, persist)
                checkCurrent(current)
                storage.commit(destination.videoPart, destination.videoFile)
            }
            if (!audio.second) {
                downloadTrack(request.audioUrl, destination.audioPart, audio.first, true, activeProgress, current, publish, persist)
                checkCurrent(current)
                storage.commit(destination.audioPart, destination.audioFile)
            }
            entry = activeProgress.current
            val coverPath = transfer.cover(entry.cover, destination.coverFile)
            checkCurrent(current)
            val bytes = storage.size(destination.videoFile) + storage.size(destination.audioFile)
            entry = entry.copy(transfer = CacheTransferState(1f, bytes, bytes, CacheStatus.Completed),
                files = entry.files.copy(videoPath = destination.videoFile, audioPath = destination.audioFile, coverPath = coverPath))
            publish(entry); persist()
        } catch (cancel: CancellationException) {
            if (cleanupAllowed()) storage.removePartial(destination)
            throw cancel
        } catch (failure: Exception) {
            // 失败时刻意保留 .part：重试时按 Range 从断点继续，而不是重新下载整条轨。
            if (!current()) return
            entry = progress?.current ?: entry
            entry = entry.copy(transfer = entry.transfer.copy(status = CacheStatus.Failed, speedBps = 0L),
                error = failure.message ?: "下载失败")
            publish(entry); persist()
        }
    }

    /** @return 已下载字节数（作为进度种子和 Range 起点）到是否该轨已完整。 */
    private fun readyTrack(part: String, target: String, expected: Long): Pair<Long, Boolean> {
        val done = storage.size(target)
        if (done > 0L && (expected <= 0L || done >= expected)) return done to true
        val partial = storage.size(part)
        if (partial > 0L && expected > 0L && partial >= expected) {
            storage.commit(part, target)
            return partial to true
        }
        return partial to false
    }

    private suspend fun downloadTrack(url: String, target: String, resumeFrom: Long, audio: Boolean, progress: CacheDownloadProgress,
        current: () -> Boolean, publish: suspend (CachedVideo) -> Unit, persist: suspend () -> Unit) {
        checkCurrent(current)
        when (val result = transfer.download(url, target, resumeFrom) { done ->
            checkCurrent(current)
            progress.update(done, audio)?.let { publish(it); if (progress.persist) persist() }
        }) {
            is Outcome.Success -> Unit
            is Outcome.Failure -> throw CacheTransferException(result.error)
        }
    }
    private suspend fun checkCurrent(current: () -> Boolean) {
        currentCoroutineContext().ensureActive()
        if (!current()) throw CancellationException("已删除")
    }
}
internal data class CacheDownloadRequest(val initial: CachedVideo, val videoUrl: String,
    val audioUrl: String, val destination: CacheDestination)
private class CacheTransferException(error: AppError) : Exception(when (error) {
    is AppError.Network -> error.message
    is AppError.Unknown -> error.message
    is AppError.Parsing -> error.message
    is AppError.RiskControl -> error.message
    AppError.LoginExpired -> "请先登录"
})
