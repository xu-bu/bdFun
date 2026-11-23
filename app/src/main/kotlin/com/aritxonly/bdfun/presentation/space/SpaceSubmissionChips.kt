package com.aritxonly.bdfun

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

internal fun androidx.compose.foundation.lazy.LazyListScope.spaceSubmissionChips(
    content: SpaceScreenContent, actions: SpaceScreenActions,
) {
    val seasons = content.snapshot.seasons
    val articles = content.snapshot.articles.items
    val ui = content.ui
    val order = content.snapshot.videoOrder
    val tabEnterModifier = Modifier
    item(key = "submission-sort") {
        // 投稿排序：点击在 从新到旧 → 播放最多 → 从旧到新 之间循环。
        val label = when (order) { 1 -> "播放最多"; 2 -> "从旧到新"; else -> "从新到旧" }
        SubmissionChip(text = "排序：$label", selected = false, onClick = {
            actions.onSetVideoOrder((order + 1) % 3)
        })
    }
    item(key = "submission-chips") {
        LazyRow(
            modifier = tabEnterModifier,
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                SubmissionChip(
                    text = "视频",
                    selected = ui.submissionMode == 0,
                    onClick = { ui.submissionMode = 0 },
                )
            }
            item {
                SubmissionChip(
                    text = "图文",
                    selected = ui.submissionMode == 1,
                    onClick = {
                        ui.submissionMode = 1
                        ui.selectedCollection = null
                        if (articles.isEmpty()) actions.onLoadArticles(1)
                    },
                )
            }
            items(seasons) { s ->
                SubmissionChip(
                    text = s.title,
                    selected = ui.submissionMode == 2 && ui.selectedCollection?.id == s.id,
                    onClick = { actions.onLoadCollection(s) },
                )
            }
            item {
                SubmissionChip(
                    text = "合集与系列",
                    selected = ui.submissionMode == 3,
                    onClick = {
                        ui.submissionMode = 3
                        ui.selectedCollection = null
                    },
                )
            }
        }
    }
}
