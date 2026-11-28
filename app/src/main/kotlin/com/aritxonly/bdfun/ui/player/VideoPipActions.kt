package com.aritxonly.bdfun.ui

import android.app.Activity
import android.app.PendingIntent
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.Icon
import android.util.Rational
import android.view.View
import android.view.ViewGroup
import kotlin.math.cos
import kotlin.math.sin

/** 画中画按钮；进度跳转与通知栏的 10 秒分开，用独立 action（由 MediaActionReceiver 处理）。 */
internal const val ACTION_PIP_REWIND = "com.aritxonly.bdfun.PIP_REWIND"
internal const val ACTION_PIP_TOGGLE = "com.aritxonly.bdfun.PIP_TOGGLE"
internal const val ACTION_PIP_FORWARD = "com.aritxonly.bdfun.PIP_FORWARD"
internal const val PIP_SEEK_MS = 15_000L
/** 播放器视图标记；画中画靠它找来源矩形，展示层不必依赖 Media3。 */
internal const val PIP_SOURCE_TAG = "bdfun_pip_source"

/**
 * 视频页进入画中画的参数：后退 15 秒 / 播放暂停 / 前进 15 秒。
 * 提供自定义按钮后系统不再按媒体会话自动生成按钮，所以播放暂停也由这里给出。
 * 设备只允许更少按钮时只保留播放暂停。
 */
internal fun videoPictureInPictureParams(
    activity: Activity, playing: Boolean, sourceRectHint: Rect? = null,
): PictureInPictureParams {
    val toggle = pipAction(activity, if (playing) "暂停" else "播放", ACTION_PIP_TOGGLE, 21,
        if (playing) pauseIcon() else playIcon())
    val all = listOf(
        pipAction(activity, "后退 15 秒", ACTION_PIP_REWIND, 20, seekIcon(forward = false)),
        toggle,
        pipAction(activity, "前进 15 秒", ACTION_PIP_FORWARD, 22, seekIcon(forward = true)),
    )
    val max = runCatching { activity.maxNumPictureInPictureActions }.getOrDefault(all.size)
    val builder = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .setActions(if (all.size <= max) all else listOf(toggle))
        .setSeamlessResizeEnabled(true)
    if (sourceRectHint != null) builder.setSourceRectHint(sourceRectHint)
    return builder.build()
}

/** 从小窗来源 rect 起步，系统才能直接把视频画面搬到角落，否则会先缩窗再盖黑遮罩。 */
internal fun pipSourceRect(activity: Activity): Rect? {
    val view = findPlayerView(activity.window?.decorView ?: return null) ?: return null
    val rect = Rect()
    return if (view.getGlobalVisibleRect(rect) && !rect.isEmpty) rect else null
}

private fun findPlayerView(view: View): View? {
    if (view.tag == PIP_SOURCE_TAG) return view
    if (view is ViewGroup) {
        for (index in 0 until view.childCount) {
            findPlayerView(view.getChildAt(index))?.let { return it }
        }
    }
    return null
}

private fun pipAction(activity: Activity, title: String, action: String, requestCode: Int, icon: Icon): RemoteAction {
    val intent = PendingIntent.getBroadcast(
        activity, requestCode, Intent(activity, MediaActionReceiver::class.java).setAction(action),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return RemoteAction(icon, title, title, intent)
}

/** 按钮图标在代码里绘制：Material 没有 15 秒图标，矢量图里也画不出可靠的数字。 */
private fun pipIcon(draw: Canvas.(Paint, Float) -> Unit): Icon {
    val size = 96
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    Canvas(bitmap).draw(paint, size.toFloat())
    return Icon.createWithBitmap(bitmap)
}

/** 顶部留缺口的圆形箭头 + 中间的「15」；后退箭头逆时针，前进箭头顺时针。 */
private fun seekIcon(forward: Boolean) = pipIcon { paint, s ->
    val inset = s * 0.14f
    val radius = (s - inset * 2) / 2f
    val cx = s / 2f
    val cy = s / 2f
    paint.style = Paint.Style.STROKE
    paint.strokeWidth = s * 0.07f
    paint.strokeCap = Paint.Cap.ROUND
    drawArc(RectF(inset, inset, s - inset, s - inset), -70f, 320f, false, paint)
    // 箭头放在缺口一侧，沿旋转方向指向缺口。
    val angle = Math.toRadians(if (forward) -110.0 else -70.0)
    val ex = cx + radius * cos(angle).toFloat()
    val ey = cy + radius * sin(angle).toFloat()
    val tx = if (forward) -sin(angle).toFloat() else sin(angle).toFloat()
    val ty = if (forward) cos(angle).toFloat() else -cos(angle).toFloat()
    val nx = cos(angle).toFloat()
    val ny = sin(angle).toFloat()
    val head = s * 0.16f
    paint.style = Paint.Style.FILL
    drawPath(Path().apply {
        moveTo(ex + tx * head, ey + ty * head)
        lineTo(ex + nx * head * 0.6f, ey + ny * head * 0.6f)
        lineTo(ex - nx * head * 0.6f, ey - ny * head * 0.6f)
        close()
    }, paint)
    paint.textSize = s * 0.32f
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    drawText("15", cx, cy - (paint.descent() + paint.ascent()) / 2f, paint)
}

private fun playIcon() = pipIcon { paint, s ->
    drawPath(Path().apply {
        moveTo(s * 0.34f, s * 0.24f)
        lineTo(s * 0.78f, s * 0.5f)
        lineTo(s * 0.34f, s * 0.76f)
        close()
    }, paint)
}

private fun pauseIcon() = pipIcon { paint, s ->
    drawRoundRect(RectF(s * 0.28f, s * 0.24f, s * 0.42f, s * 0.76f), s * 0.03f, s * 0.03f, paint)
    drawRoundRect(RectF(s * 0.58f, s * 0.24f, s * 0.72f, s * 0.76f), s * 0.03f, s * 0.03f, paint)
}
