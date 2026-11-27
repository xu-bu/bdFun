package com.aritxonly.bdfun.core.domain.update

/** 发布源固定为本仓库；保留旧上游前缀仅为兼容历史 Release 链接。 */
const val RELEASES_API_URL = "https://api.github.com/repos/xu-bu/bdFun/releases"

private const val CURRENT_DOWNLOAD_PREFIX = "https://github.com/xu-bu/bdFun/releases/download/"
private const val LEGACY_DOWNLOAD_PREFIX = "https://github.com/QxTeak/bdFun/releases/download/"

private val RELEASE_DOWNLOAD_PREFIXES = listOf(CURRENT_DOWNLOAD_PREFIX, LEGACY_DOWNLOAD_PREFIX)

fun isReleaseDownloadUrl(url: String): Boolean =
    url.endsWith(".apk", ignoreCase = true) && RELEASE_DOWNLOAD_PREFIXES.any(url::startsWith)
