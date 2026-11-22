package com.aritxonly.bdfun.infra.bili

import com.aritxonly.bdfun.core.common.*
import com.aritxonly.bdfun.core.domain.VideoCacheTransfer
import kotlinx.coroutines.*
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.TimeUnit

class BiliVideoCacheTransfer(private val client: OkHttpClient = defaultCacheTransferClient()) : VideoCacheTransfer {
    override suspend fun download(
        url: String,
        target: String,
        onBytes: suspend (Long) -> Unit,
    ): Outcome<Long> = download(url, target, 0L, onBytes)

    override suspend fun download(
        url: String,
        target: String,
        resumeFrom: Long,
        onBytes: suspend (Long) -> Unit,
    ): Outcome<Long> = withContext(Dispatchers.IO) {
        val file = File(target)
        transferResult {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BiliBackend.UA)
                .header("Referer", "https://www.bilibili.com/")
                .apply { if (resumeFrom > 0L) header("Range", "bytes=$resumeFrom-") }
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IOException("HTTP ${response.code}")
                }
                // 只有 206 且 Content-Range 起点精确匹配才能追加；服务端忽略 Range 时
                // 会返回 200，此时必须截断重下，否则文件会拼接出坏数据。
                val appending = resumeFrom > 0L && response.code == 206 &&
                    response.header("Content-Range")?.trim()?.startsWith("bytes $resumeFrom-") == true
                val body = response.body ?: throw IOException("空响应")
                var copied = 0L
                val output = if (appending) FileOutputStream(file, true) else file.outputStream()
                output.use { out ->
                    body.byteStream().use { input ->
                        val buffer = ByteArray(256 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read <= 0) break
                            out.write(buffer, 0, read)
                            copied += read
                            onBytes(if (appending) resumeFrom + copied else copied)
                        }
                    }
                }
                if (appending) resumeFrom + copied else copied
            }
        }
    }

    override suspend fun contentLength(url: String): Long = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BiliBackend.UA)
                .header("Referer", "https://www.bilibili.com/")
                .head()
                .build()
            client.newCall(request).execute().use { response ->
                response.header("Content-Length")?.toLongOrNull() ?: -1L
            }
        }.getOrDefault(-1L)
    }

    override suspend fun cover(url: String, target: String): String {
        if (url.isBlank()) return ""
        val file = File(target)
        return runCatching {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", BiliBackend.UA)
                .header("Referer", "https://www.bilibili.com/")
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return ""
                val body = response.body ?: return ""
                file.outputStream().use { output ->
                    body.byteStream().use { input -> input.copyTo(output) }
                }
            }
            if (file.length() > 0) file.absolutePath else ""
        }.getOrDefault("")
    }

}

internal suspend fun <T> transferResult(block: suspend () -> T): Outcome<T> = try {
    Outcome.Success(block())
} catch (cancel: CancellationException) {
    throw cancel
} catch (failure: Exception) {
    Outcome.Failure(AppError.Network(failure.message ?: "下载失败"))
}

internal fun defaultCacheTransferClient(): OkHttpClient = HttpClients.base.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        // 关键：base 设了 callTimeout(18s) 作为「整个请求」的总时限，这里下载动辄几百 MB，
        // 派生客户端若不覆盖就会被 18 秒掐断，表现为高频随机 timeout。下载必须禁用总时限，
        // 只靠 connect/read timeout 控制单段无响应的情况。
        .callTimeout(0, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
