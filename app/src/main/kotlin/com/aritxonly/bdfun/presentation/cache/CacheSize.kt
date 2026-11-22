package com.aritxonly.bdfun.ui



internal fun formatCacheSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes / 1024.0 / 1024.0
    return if (mb >= 1024) {
        "%.2f GB".format(mb / 1024)
    } else {
        "%.1f MB".format(mb)
    }
}

internal fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond <= 0L -> "计算中"
    bytesPerSecond < 1024L -> "$bytesPerSecond B/s"
    bytesPerSecond < 1024L * 1024L -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
    else -> "%.1f MB/s".format(bytesPerSecond / 1024.0 / 1024.0)
}
