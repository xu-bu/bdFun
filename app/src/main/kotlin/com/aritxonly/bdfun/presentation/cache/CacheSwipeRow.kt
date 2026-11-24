package com.aritxonly.bdfun.ui

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private val CacheDeleteWidth = 88.dp
private val CacheDeleteColor = Color(0xFFE5484D)

/**
 * 缓存列表项的「管理模式」：左滑露出删除键，点内容区收起。
 * 状态放在外部（按缓存 id 记录），保证同一时刻只有一项处于管理模式。
 */
@Composable
internal fun CacheSwipeRow(
    isRevealed: Boolean,
    onReveal: () -> Unit,
    onCollapse: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val scope = rememberCoroutineScope()
    val revealPx = with(LocalDensity.current) { CacheDeleteWidth.toPx() }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var animationJob by remember { mutableStateOf<Job?>(null) }

    fun settle(target: Float) {
        animationJob?.cancel()
        val start = offsetX
        animationJob = scope.launch {
            animate(start, target, animationSpec = spring<Float>(stiffness = 380f, dampingRatio = 0.85f)) { value, _ ->
                offsetX = value
            }
        }
    }

    LaunchedEffect(isRevealed, revealPx) {
        settle(if (isRevealed) -revealPx else 0f)
    }

    Box(
        modifier = modifier.clip(androidx.compose.foundation.shape.RoundedCornerShape(16.dp)),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .width(CacheDeleteWidth)
                .fillMaxHeight()
                .clickable(interactionSource = null, indication = null, onClick = onDelete),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "删除", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        }
        Box(
            modifier = Modifier
                .offset { IntOffset(offsetX.roundToInt(), 0) }
                .pointerInput(revealPx) {
                    detectHorizontalDragGestures(
                        onHorizontalDrag = { change, dragAmount ->
                            change.consume()
                            animationJob?.cancel()
                            offsetX = (offsetX + dragAmount).coerceIn(-revealPx, 0f)
                        },
                        onDragEnd = {
                            val opened = offsetX <= -revealPx * 0.5f
                            settle(if (opened) -revealPx else 0f)
                            if (opened) onReveal() else onCollapse()
                        },
                        onDragCancel = { settle(if (offsetX <= -revealPx * 0.5f) -revealPx else 0f) },
                    )
                }
                .clickable(interactionSource = null, indication = null, enabled = isRevealed) {
                    onCollapse()
                },
        ) {
            content()
        }
    }
}
