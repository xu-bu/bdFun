package com.aritxonly.bdfun.core.application

import com.aritxonly.bdfun.core.common.*
import com.aritxonly.bdfun.core.model.cache.*
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class DownloadQueueStateHolderTest {
    @Test fun requiresInitializationAndAudioAndRejectsDuplicateActiveOrCompletedEntries() = runBlocking {
        val files = CacheFilesFake(); val transfer = CacheTransferFake(files).apply { gate = CompletableDeferred() }
        val queue = cacheQueue(files, transfer)
        assertFalse(queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12))
        queue.initialize()
        assertFalse(queue.enqueue(cacheDetail(), cacheQuality(), 7, null, 12))
        assertFalse(queue.enqueue(cacheDetail(), cacheQuality(), 7, "  ", 12))
        assertTrue(queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12))
        assertTrue(queue.hasActive()); assertFalse(queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12))
        transfer.gate!!.complete(Unit)
        assertEquals(CacheStatus.Completed, queue.items.single().status)
        assertFalse(queue.hasActive()); assertFalse(queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12))
        queue.close()
    }
    @Test fun transfersVideoThenAudioAndPublishesActualFinalFilesAndBytes() = runBlocking {
        val files = CacheFilesFake(); val transfer = CacheTransferFake(files); var starts = 0
        val queue = cacheQueue(files, transfer, start = { starts++ }); queue.initialize()
        assertTrue(queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12))
        val entry = queue.items.single()
        assertEquals("BV1234567890_7_80", entry.id); assertEquals(1, starts)
        assertEquals(listOf("HEAD:video", "HEAD:audio", "GET:video", "GET:audio", "COVER:https://cover"), transfer.calls)
        assertEquals(CacheTransferState(1f, 120, 120, CacheStatus.Completed), entry.transfer)
        assertEquals("${entry.id}/video.m4s", entry.videoPath); assertEquals("${entry.id}/audio.m4s", entry.audioPath)
        assertEquals("${entry.id}/cover.jpg", entry.coverPath)
        assertTrue(files.written.any { it.single().status == CacheStatus.Queued })
        assertEquals(CacheStatus.Completed, files.written.last().single().status)
        queue.close()
    }
    @Test fun failedTransferRetainsPublishedProgressAndCanRetry() = runBlocking {
        val files = CacheFilesFake(); val transfer = CacheTransferFake(files).apply { result = Outcome.Failure(AppError.Network("offline")) }
        val queue = cacheQueue(files, transfer); queue.initialize(); queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12)
        val failed = queue.items.single()
        assertEquals(CacheStatus.Failed, failed.status); assertEquals("offline", failed.error)
        assertEquals(100L, failed.bytes); assertEquals(120L, failed.totalBytes)
        // 失败保留 video.part 供续传；audio 尚未开始所以没有 .part。
        assertEquals(100L, files.sizes["${failed.id}/video.part"])
        assertFalse(files.sizes.containsKey("${failed.id}/audio.part"))
        assertNull(queue.findPlayable(failed.bvid, 7))
        transfer.result = null; assertTrue(queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12))
        assertEquals(CacheStatus.Completed, queue.items.single().status); queue.close()
    }
    @Test fun retryResolvesFreshUrlsAndCompletesFailedEntry() = runBlocking {
        val files = CacheFilesFake()
        val transfer = CacheTransferFake(files).apply { result = Outcome.Failure(AppError.Network("timeout")) }
        val queue = cacheQueue(files, transfer); queue.initialize()
        assertTrue(queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12))
        assertEquals(CacheStatus.Failed, queue.items.single().status)

        // 未注入解析器时不可重试
        assertFalse(queue.retry(queue.items.single().id))

        var resolvedCover: String? = null
        queue.setRetryResolver { entry ->
            resolvedCover = entry.cover
            Outcome.Success(CacheRetryUrls("video", "audio"))
        }
        transfer.result = null
        assertTrue(queue.retry(queue.items.single().id))
        // Unconfined 下重试协程同步跑完
        val after = queue.items.single()
        assertEquals(CacheStatus.Completed, after.status)
        assertNull(after.error)
        assertEquals("https://cover", resolvedCover)
        assertTrue(transfer.calls.contains("GET:video"))
        queue.close()
    }

    @Test fun retryResumesVideoFromPartialBytes() = runBlocking {
        val files = CacheFilesFake()
        var firstAttempt = true
        val transfer = object : CacheTransferFake(files) {
            override suspend fun download(url: String, target: String, resumeFrom: Long,
                onBytes: suspend (Long) -> Unit): Outcome<Long> {
                calls += "GET:$url"; resumeCalls += "$url@$resumeFrom"; gate?.await()
                if (firstAttempt && url == "video") {
                    firstAttempt = false
                    storage.sizes[target] = 40; clock += 220; onBytes(40)
                    return Outcome.Failure(AppError.Network("cut"))
                }
                val bytes = (if (url == "video") 100L else 20L) - resumeFrom
                storage.sizes[target] = resumeFrom + bytes; clock += 220; onBytes(resumeFrom + bytes)
                return Outcome.Success(resumeFrom + bytes)
            }
        }
        val queue = cacheQueue(files, transfer); queue.initialize()
        queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12)
        val id = queue.items.single().id
        assertEquals(CacheStatus.Failed, queue.items.single().status)
        assertEquals(40L, files.sizes["$id/video.part"])
        assertEquals(40L, queue.items.single().bytes)

        queue.setRetryResolver { Outcome.Success(CacheRetryUrls("video", "audio")) }
        assertTrue(queue.retry(id))
        val after = queue.items.single()
        assertEquals(CacheStatus.Completed, after.status)
        assertTrue(transfer.resumeCalls.contains("video@40"))
        assertTrue(transfer.resumeCalls.contains("audio@0"))
        assertEquals(120L, after.bytes)
        queue.close()
    }

    @Test fun retrySkipsTrackAlreadyCommittedBeforeFailure() = runBlocking {
        val files = CacheFilesFake()
        var failAudio = true
        val transfer = object : CacheTransferFake(files) {
            override suspend fun download(url: String, target: String, resumeFrom: Long,
                onBytes: suspend (Long) -> Unit): Outcome<Long> {
                calls += "GET:$url"; resumeCalls += "$url@$resumeFrom"; gate?.await()
                if (failAudio && url == "audio") return Outcome.Failure(AppError.Network("offline"))
                val bytes = (if (url == "video") 100L else 20L) - resumeFrom
                storage.sizes[target] = resumeFrom + bytes; clock += 220; onBytes(resumeFrom + bytes)
                return Outcome.Success(resumeFrom + bytes)
            }
        }
        val queue = cacheQueue(files, transfer); queue.initialize()
        queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12)
        val id = queue.items.single().id
        assertEquals(CacheStatus.Failed, queue.items.single().status)
        assertEquals(100L, files.sizes["$id/video.m4s"])
        assertFalse(files.sizes.containsKey("$id/audio.part"))
        val videoGets = transfer.calls.count { it == "GET:video" }

        failAudio = false
        queue.setRetryResolver { Outcome.Success(CacheRetryUrls("video", "audio")) }
        assertTrue(queue.retry(id))
        assertEquals(CacheStatus.Completed, queue.items.single().status)
        assertEquals(videoGets, transfer.calls.count { it == "GET:video" })
        assertEquals(2, transfer.calls.count { it == "GET:audio" })
        queue.close()
    }

    @Test fun retryResolverFailureWritesErrorBackAndStaysFailed() = runBlocking {
        val files = CacheFilesFake()
        val transfer = CacheTransferFake(files).apply { result = Outcome.Failure(AppError.Network("timeout")) }
        val queue = cacheQueue(files, transfer); queue.initialize()
        assertTrue(queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12))
        val id = queue.items.single().id
        assertEquals(CacheStatus.Failed, queue.items.single().status)
        queue.setRetryResolver { Outcome.Failure(AppError.Network("登录失效")) }
        assertTrue(queue.retry(id))
        val after = queue.items.single()
        assertEquals(CacheStatus.Failed, after.status)
        assertTrue(after.error!!.contains("登录失效"))
        queue.close()
    }

    @Test fun retryIgnoresNonFailedEntries() = runBlocking {
        val files = CacheFilesFake(); val transfer = CacheTransferFake(files).apply { gate = CompletableDeferred() }
        val queue = cacheQueue(files, transfer); queue.initialize()
        queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12)
        queue.setRetryResolver { Outcome.Success(CacheRetryUrls("video", "audio")) }
        // 正在下载，不是 Failed，retry 应拒绝
        assertFalse(queue.retry(queue.items.single().id))
        queue.close()
    }

    @Test fun deletionCancelsTaskRemovesFilesAndDoesNotPublishFailureRow() = runBlocking {
        val files = CacheFilesFake(); val transfer = CacheTransferFake(files).apply { gate = CompletableDeferred() }
        val queue = cacheQueue(files, transfer); queue.initialize(); queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12)
        queue.delete(queue.items.single().id); transfer.gate!!.complete(Unit)
        assertTrue(queue.items.isEmpty()); assertFalse(queue.hasActive()); assertTrue(files.sizes.isEmpty())
        assertTrue(files.written.last().isEmpty()); queue.close()
    }
    @Test fun sameIdReplacementSurvivesOldCallbacksCleanupAndFinally() = runBlocking {
        val files = CacheFilesFake(); val oldGate = CompletableDeferred<Unit>(); val newGate = CompletableDeferred<Unit>()
        val running = mutableListOf<Job>()
        val transfer = object : CacheTransferFake(files) {
            override suspend fun download(url: String, target: String, onBytes: suspend (Long) -> Unit): Outcome<Long> =
                download(url, target, 0L, onBytes)
            override suspend fun download(url: String, target: String, resumeFrom: Long,
                onBytes: suspend (Long) -> Unit): Outcome<Long> {
                val ordinal = running.size; running += currentCoroutineContext().job
                storage.sizes[target] = if (ordinal == 0) 1 else 2
                if (ordinal == 0) withContext(NonCancellable) { oldGate.await(); onBytes(1) }
                else { newGate.await(); onBytes(2) }
                return Outcome.Success(2)
            }
        }
        val queue = cacheQueue(files, transfer); queue.initialize(); queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12)
        val id = queue.items.single().id; queue.delete(id)
        queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12)
        assertEquals(2, running.size); oldGate.complete(Unit); running[0].join()
        assertEquals(2L, files.sizes["$id/video.part"])
        assertEquals(CacheStatus.Downloading, queue.items.single().status)
        queue.delete(id); assertTrue(running[1].isCancelled)
        assertTrue(queue.items.isEmpty()); queue.close()
    }
    @Test fun closePreventsNewWorkAndCancelledJobDoesNotBecomeFailure() = runBlocking {
        val files = CacheFilesFake(); val transfer = CacheTransferFake(files).apply { gate = CompletableDeferred() }
        val queue = cacheQueue(files, transfer); queue.initialize(); queue.enqueue(cacheDetail(), cacheQuality(), 7, "audio", 12)
        queue.close(); transfer.gate!!.complete(Unit)
        assertFalse(queue.enqueue(cacheDetail(), cacheQuality(), 8, "audio", 12))
        assertFalse(queue.items.any { it.status == CacheStatus.Failed })
    }
}
