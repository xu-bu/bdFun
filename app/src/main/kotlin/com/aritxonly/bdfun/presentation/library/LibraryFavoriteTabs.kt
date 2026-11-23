package com.aritxonly.bdfun.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalLocale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.aritxonly.bdfun.core.model.library.FavoriteFolder
import com.aritxonly.bdfun.core.model.pgc.BangumiCard
import com.aritxonly.teak.common.navigation.TeakPagerTabs
import com.aritxonly.teak.common.navigation.TeakTabIndicatorStyle
import top.yukonga.miuix.kmp.theme.MiuixTheme
import java.util.Date
import java.util.Locale

/** 收藏页二级 tab：五个标签在窄屏可横滑，选中态与 PC 版一致（粉色文字 + 下划线）。 */
@Composable
internal fun LibraryFavoriteSubTabs(selected: Int, onSelect: (Int) -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        TeakPagerTabs(
            options = FAVORITE_SUB_TABS,
            selected = selected,
            onSelect = onSelect,
            itemWidth = 88.dp,
            backdrop = null,
            plain = true,
            transparentBackground = true,
            indicatorStyle = TeakTabIndicatorStyle.Underline,
        )
    }
}

/** 收藏夹 / UGC 订阅合集卡片：封面 + 数量 + 私密或日期信息。 */
@Composable
internal fun LibraryFavoriteFolderCard(folder: FavoriteFolder, onClick: () -> Unit) {
    val colors = MiuixTheme.colorScheme
    val ugc = folder.type == 21
    val locale = LocalLocale.current.platformLocale
    val meta = buildList {
        if (folder.count > 0) add("${folder.count} 个视频")
        if (ugc) add("合集")
        if (folder.privateFolder) add("私密")
        if (!ugc && folder.createdAt > 0L) {
            add(runCatching {
                java.text.SimpleDateFormat("yyyy-MM-dd", locale).format(Date(folder.createdAt * 1000L))
            }.getOrDefault(""))
        }
    }.filter { it.isNotBlank() }.joinToString(" · ")
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.surfaceContainer)
            .clickable(interactionSource = null, indication = null, onClick = onClick),
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(colors.surfaceContainerHigh),
        ) {
            if (folder.cover.isNotBlank()) {
                AsyncImage(
                    model = rememberImageRequest(folder.cover),
                    contentDescription = folder.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }
        Column(Modifier.padding(horizontal = 7.dp, vertical = 7.dp)) {
            Text(
                text = folder.title,
                color = colors.onBackground,
                fontSize = 12.5.sp,
                lineHeight = 16.5.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.heightIn(min = 33.dp),
            )
            Spacer(Modifier.height(2.dp))
            Box(Modifier.height(16.dp), contentAlignment = Alignment.CenterStart) {
                if (meta.isNotBlank()) {
                    Text(
                        text = meta,
                        color = colors.onBackgroundVariant,
                        fontSize = 10.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/** 追番/追剧卡片直接复用番剧页组件，保持封面比例和角标一致。 */
@Composable
internal fun LibraryBangumiCard(item: BangumiCard, onClick: () -> Unit) {
    com.aritxonly.bdfun.presentation.pgc.BangumiCardItem(item, onClick)
}
