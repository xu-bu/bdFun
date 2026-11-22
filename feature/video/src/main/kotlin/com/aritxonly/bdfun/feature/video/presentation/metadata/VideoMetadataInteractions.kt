package com.aritxonly.bdfun.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal fun VideoMetadataInteractions(interaction: com.aritxonly.bdfun.feature.video.application.VideoInteractionState, actions: VideoMetadataActions, appearance: VideoMetadataAppearance, slots: VideoMetadataSlots) {
    val disliked = interaction.disliked
    val coined = interaction.coined
    val coinCount = interaction.coinCount
    val favorited = interaction.favorited
    val favoriteCount = interaction.favoriteCount
    val shareCount = interaction.shareCount
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        ActionStat(
            modifier = Modifier.weight(1f),
            label = "缓存",
            value = 0,
            kind = ActionKind.Download,
            iconRes = appearance.icons.cache,
            accentColor = appearance.accentColor,
            activity = slots.activity,
            onClick = { actions.cacheVideo() },
        )
        ActionStat(
            modifier = Modifier.weight(1f),
            label = "不喜欢",
            value = 0,
            kind = ActionKind.ThumbDown,
            iconRes = appearance.icons.dislike,
            accentColor = appearance.accentColor,
            activity = slots.activity,
            active = disliked,
            onClick = { actions.toggleDislike() },
        )
        ActionStat(
            modifier = Modifier.weight(1f),
            label = "投币",
            value = coinCount,
            kind = ActionKind.Coin,
            iconRes = appearance.icons.coin,
            accentColor = appearance.accentColor,
            activity = slots.activity,
            active = coined,
            onClick = { actions.giveCoin() },
        )
        ActionStat(
            modifier = Modifier.weight(1f),
            label = "收藏",
            value = favoriteCount,
            kind = ActionKind.Star,
            iconRes = appearance.icons.favorite,
            accentColor = appearance.accentColor,
            activity = slots.activity,
            active = favorited,
            onClick = { actions.toggleFavorite() },
        )
        ActionStat(
            modifier = Modifier.weight(1f),
            label = "分享",
            value = shareCount,
            kind = ActionKind.Share,
            iconRes = appearance.icons.share,
            accentColor = appearance.accentColor,
            activity = slots.activity,
            active = false,
            onClick = { actions.shareVideo() },
        )
    }
}
