package com.aritxonly.bdfun.core.domain

import com.aritxonly.bdfun.core.common.Outcome
import com.aritxonly.bdfun.core.model.user.*
import com.aritxonly.bdfun.core.model.feed.FeedPage
import com.aritxonly.bdfun.core.model.video.VideoItem

interface SpaceRepository {
    suspend fun searchVideos(mid: Long, keyword: String, page: Int): Outcome<List<VideoItem>>
    suspend fun block(mid: Long): Outcome<Boolean>
    suspend fun profile(mid: Long): Outcome<SpaceInfo>
    suspend fun seasons(mid: Long): Outcome<List<SpaceSeason>>
    suspend fun dynamics(mid: Long, offset: String): Outcome<FeedPage>
    suspend fun relation(mid: Long): Outcome<Int?>
    /** 投稿列表。order：0 从新到旧 / 1 播放最多 / 2 从旧到新；默认 0 兼容既有调用。 */
    suspend fun videos(mid: Long, page: Int, order: Int = 0): Outcome<List<VideoItem>>
    suspend fun articles(mid: Long, page: Int): Outcome<List<SpaceArticle>>
    suspend fun bangumi(mid: Long, page: Int): Outcome<List<SpaceBangumi>>
    suspend fun shop(mid: Long): Outcome<List<SpaceShopItem>>
    suspend fun collection(mid: Long, collection: SpaceSeason): Outcome<List<VideoItem>>
    suspend fun follow(mid: Long, followed: Boolean): Outcome<Boolean>
}
