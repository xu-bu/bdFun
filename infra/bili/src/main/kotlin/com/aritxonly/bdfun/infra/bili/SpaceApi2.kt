package com.aritxonly.bdfun.infra.bili

import android.content.Context
import android.net.Uri
import android.text.Html
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Dispatcher
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream
import java.util.zip.InflaterInputStream
import kotlin.math.roundToInt
import com.aritxonly.bdfun.core.domain.*
import com.aritxonly.bdfun.core.model.video.*
import com.aritxonly.bdfun.core.model.comment.*
import com.aritxonly.bdfun.core.model.user.*
import com.aritxonly.bdfun.core.model.feed.*
import com.aritxonly.bdfun.core.model.message.*
import com.aritxonly.bdfun.core.model.library.*
import com.aritxonly.bdfun.infra.bili.mapper.*
import com.aritxonly.bdfun.infra.bili.BILI_API as API
import com.aritxonly.bdfun.infra.bili.BILI_APP_API as APP_API
import com.aritxonly.bdfun.infra.bili.BILI_APP_UA as APP_UA
import com.aritxonly.bdfun.infra.bili.BILI_APP as APP
import com.aritxonly.bdfun.infra.bili.BILI_APP_KEY as APP_KEY
import com.aritxonly.bdfun.infra.bili.BILI_APP_SEC as APP_SEC
import com.aritxonly.bdfun.infra.bili.BILI_TV_APP_KEY as TV_APP_KEY
import com.aritxonly.bdfun.infra.bili.BILI_TV_APP_SEC as TV_APP_SEC
import com.aritxonly.bdfun.infra.bili.BILI_APP_HEADERS as APP_HEADERS
import com.aritxonly.bdfun.infra.bili.BILI_PASSPORT as PASSPORT
import com.aritxonly.bdfun.infra.bili.BILI_VC as VC
import com.aritxonly.bdfun.infra.bili.BILI_UA as UA
import com.aritxonly.bdfun.infra.bili.encodeBiliParameter as encode
import com.aritxonly.bdfun.infra.bili.biliMd5 as md5
import com.aritxonly.bdfun.infra.bili.biliImage as image
import com.aritxonly.bdfun.infra.bili.biliAvatar as avatar
import com.aritxonly.bdfun.infra.bili.cleanBiliHtml as cleanHtml
import com.aritxonly.bdfun.infra.bili.biliAidToBvid as aidToBvid
import com.aritxonly.bdfun.infra.bili.parseBiliCountText as parseCountText
import com.aritxonly.bdfun.infra.bili.parseBiliDurationText as parseDurationText
import com.aritxonly.bdfun.infra.bili.biliQualityLabel as qualityLabel

/**
 * 投稿列表。order：0 从新到旧 / 1 播放最多 / 2 从旧到新。
 * B 站 arc/search 只支持 pubdate(降序)、click、stow，没有升序；
 * 从旧到新用 pubdate 取「倒数第 page 页」再页内反转来近似全局升序。
 */
internal suspend fun BiliApiContext.spaceVideos(mid: Long, page: Int, order: Int = 0): List<VideoItem> = withContext(Dispatchers.IO) {
    val apiOrder = if (order == 1) "click" else "pubdate"
    if (order != 2) return@withContext spaceVideosPage(mid, page, apiOrder).first
    // 升序：第一次请求拿总页数，再取倒数对应页并反转。
    val probe = spaceVideosPage(mid, 1, apiOrder)
    val totalPages = probe.second.coerceAtLeast(1)
    val target = totalPages - page + 1
    if (target < 1) return@withContext emptyList()
    val items = if (page == 1) probe.first else spaceVideosPage(mid, target, apiOrder).first
    items.asReversed()
}

/** 取一页投稿，返回 (条目, 总页数)。 */
private suspend fun BiliApiContext.spaceVideosPage(mid: Long, page: Int, apiOrder: String): Pair<List<VideoItem>, Int> =
    cancellableCatching {
        val data = dataRequest(
            "/x/space/wbi/arc/search",
            mapOf(
                "mid" to mid.toString(),
                "pn" to page.toString(),
                "ps" to "30",
                "order" to apiOrder,
                "web_location" to "1550101",
                "dm_img_list" to "[]",
                "dm_img_str" to "V2ViR0wgMS4wIChPcGVuR0wgRVMgMi4wIENocm9taXVtKQ",
                "dm_cover_img_str" to "QU5HTEUgKE5WSURJQSwgTlZJRElBIEdlRm9yY2UgR1RYIDEwNjAgNkdCIERpcmVjdDNEMTEp",
                "dm_img_inter" to "{\"ds\":[],\"wh\":[0,0,0],\"of\":[0,0,0]}",
            ),
            signed = true,
            referer = "https://space.bilibili.com/$mid",
        )
        val items = mapSpaceVideos(data, this@spaceVideosPage::parseSpaceVideo)
        val pages = data.optJSONObject("page")?.optInt("pages", 0) ?: 0
        items to pages
    }.getOrDefault(emptyList<VideoItem>() to 0)

/** 空间图文投稿。 */

internal suspend fun BiliApiContext.spaceArticles(mid: Long, page: Int): List<SpaceArticle> =
    withContext(Dispatchers.IO) {
        cancellableCatching {
            val data = dataRequest(
                "/x/space/wbi/article",
                mapOf(
                    "mid" to mid.toString(),
                    "pn" to page.toString(),
                    "ps" to "20",
                    "sort" to "publish_time",
                ),
                signed = true,
                referer = "https://space.bilibili.com/$mid",
            )
            mapSpaceArticles(data)
        }.getOrDefault(emptyList())
    }

/** 空间追番。 */

internal suspend fun BiliApiContext.spaceBangumi(mid: Long, page: Int): List<SpaceBangumi> =
    withContext(Dispatchers.IO) {
        cancellableCatching {
            val data = dataRequest(
                "/x/space/bangumi",
                mapOf(
                    "mid" to mid.toString(),
                    "pn" to page.toString(),
                    "ps" to "30",
                ),
                referer = "https://space.bilibili.com/$mid",
            )
            mapSpaceBangumi(data)
        }.getOrDefault(emptyList())
    }

/** UP 主小店：mall.bilibili.com 的 App 签名 POST 接口，先取第一页 8 个。 */

internal suspend fun BiliApiContext.spaceShop(mid: Long): List<SpaceShopItem> = withContext(Dispatchers.IO) {
    cancellableCatching {
        val extras = mutableMapOf(
            "actionKey" to "appkey",
            "build" to "8430300",
            "mVersion" to "309",
            "mallVersion" to "8430300",
            "statistics" to
                "{\"appId\":1,\"platform\":3,\"version\":\"8.43.0\",\"abtest\":\"\"}",
        )
        signAppParams(extras)
        val query = extras.entries.joinToString("&") { (key, value) ->
            "${android.net.Uri.encode(key)}=${android.net.Uri.encode(value)}"
        }
        val body = JSONObject()
            .put("from", "cps_productTab_$mid")
            .put("searchAfter", 0)
            .put("msource", "cps_productTab_$mid")
            .put("pageSize", 8)
            .put("upMid", mid.toString())
            .toString()
        val request = Request.Builder()
            .url("https://mall.bilibili.com/community-hub/small_shop/feed/tab/item?$query")
            .post(body.toRequestBody("application/json".toMediaTypeOrNull()))
            .header("User-Agent", APP_UA)
            .header("Referer", "https://mall.bilibili.com/")
            .header("Origin", "https://mall.bilibili.com")
            .build()
        val root = http.executeOnce(request, tolerateEmptyBody = true)
        if (root.optInt("code", 0) != 0) return@cancellableCatching emptyList()
        val data = root.optJSONObject("data") ?: return@cancellableCatching emptyList()
        mapSpaceShop(data)
    }.getOrDefault(emptyList())
}

// ---------------------------------------------------------------- search

/**
 * 搜索词联想：PiliPlus 同款 s.search.bilibili.com/main/suggest。
 * 返回 term 作为真正提交的词，name 里可能带高亮 HTML，这里清洗掉。
 */

internal suspend fun BiliApiContext.userBrief(mid: Long): Pair<String, String>? = withContext(Dispatchers.IO) {
    cancellableCatching {
        val data = dataRequest(
            "/x/space/wbi/acc/info",
            mapOf("mid" to mid.toString()),
            signed = true,
        )
        readSpaceBrief(data)
    }.getOrNull()
}

internal suspend fun BiliApiContext.userIpLocation(mid: Long): String = withContext(Dispatchers.IO) {
    cancellableCatching {
        val data = dataRequest(
            "/x/space/wbi/acc/info",
            mapOf(
                "mid" to mid.toString(),
                "web_location" to "1550101",
            ),
            signed = true,
            referer = "https://space.bilibili.com/$mid",
        )
        readSpaceIpLocation(data)
    }.getOrDefault("")
}
