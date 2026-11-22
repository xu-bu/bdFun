package com.aritxonly.bdfun.core.model.library

data class FavoriteFolder(
    val id: Long,
    val title: String,
    val count: Int,
    val cover: String,
    /** 21 = UGC 订阅合集；其它值为普通收藏夹（0 表示接口未提供）。 */
    val type: Int = 0,
    /** attr 的 bit 1 表示私密收藏夹；接口未提供时保持 false，不猜测。 */
    val privateFolder: Boolean = false,
    /** 创建时间（秒）；0 表示接口未提供。 */
    val createdAt: Long = 0L,
)
