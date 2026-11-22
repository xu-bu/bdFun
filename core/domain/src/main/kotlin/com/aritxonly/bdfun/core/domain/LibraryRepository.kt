package com.aritxonly.bdfun.core.domain

import com.aritxonly.bdfun.core.common.AppError
import com.aritxonly.bdfun.core.common.Outcome
import com.aritxonly.bdfun.core.model.library.*
import com.aritxonly.bdfun.core.model.pgc.BangumiCard
import com.aritxonly.bdfun.core.model.video.VideoItem

interface LibraryRepository {
    suspend fun folders(): Outcome<List<FavoriteFolder>>
    suspend fun videos(folderId: Long, page: Int): Outcome<Pair<List<VideoItem>, Boolean>>
    // 以下只读能力为增量接口，默认失败以免破坏未接入的调用方/测试替身。
    /** 服务端识别的默认收藏夹 ID（不依赖名称或位置）；失败/缺失时返回 0。 */
    suspend fun defaultFolderId(): Outcome<Long> = Outcome.Failure(AppError.Unknown("默认收藏夹识别不可用"))
    suspend fun createdFolders(page: Int): Outcome<Pair<List<FavoriteFolder>, Boolean>> =
        Outcome.Failure(AppError.Unknown("创建的收藏夹不可用"))
    suspend fun collectedFolders(page: Int): Outcome<Pair<List<FavoriteFolder>, Boolean>> =
        Outcome.Failure(AppError.Unknown("收藏与订阅不可用"))
    /** UGC 订阅合集内的视频；一次可能返回全部内容，分页由服务端 media_count 决定。 */
    suspend fun seasonVideos(seasonId: Long, page: Int): Outcome<Pair<List<VideoItem>, Boolean>> =
        Outcome.Failure(AppError.Unknown("订阅合集不可用"))
    /** type=1 追番、type=2 追剧。 */
    suspend fun bangumi(page: Int, type: Int): Outcome<Pair<List<BangumiCard>, Boolean>> =
        Outcome.Failure(AppError.Unknown("追番列表不可用"))
    suspend fun history(max: Long, viewAt: Long, business: String): Outcome<HistoryPage>
    suspend fun enrich(items: List<HistoryItem>): Outcome<List<HistoryItem>>
    suspend fun watchLater(): Outcome<List<HistoryItem>>
    suspend fun removeFavorite(aid: Long, folderId: Long): Outcome<Boolean>
    suspend fun addFavorite(aid: Long, folderId: Long): Outcome<Boolean>
    suspend fun deleteHistory(aid: Long): Outcome<Boolean>
    suspend fun deleteWatchLater(aid: Long): Outcome<Boolean>
}
