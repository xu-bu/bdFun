package com.aritxonly.bdfun.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage

@Composable
internal fun rememberVideoDetailTailSlots(): VideoDetailTailSlots = remember {
    VideoDetailTailSlots(
        cover = { cover, modifier ->
            AsyncImage(model = rememberImageRequest(cover), contentDescription = null,
                contentScale = ContentScale.Crop, modifier = modifier)
        },
        relatedRow = { item, onClick ->
            val context = androidx.compose.ui.platform.LocalContext.current
            val scope = androidx.compose.runtime.rememberCoroutineScope()
            RelatedRow(item = item, onClick = onClick, onCacheVideo = { enqueueVideoCache(context, scope, it) })
        },
        watchProgress = { bvid -> WatchProgressBar(bvid) },
    )
}
