package com.jianji.note.widget

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable

/** 小组件外观：配色调板与圆角背景位图。 */
object WidgetStyle {

    /** 顶部/底部色可选值。 */
    val COLORS = listOf(
        0xFF3F51B5.toInt() to "靛蓝",
        0xFF00796B.toInt() to "青绿",
        0xFF1976D2.toInt() to "蓝色",
        0xFF7B1FA2.toInt() to "紫色",
        0xFFE64A19.toInt() to "橙红",
        0xFF37474F.toInt() to "石墨",
        0xFF1B1B1B.toInt() to "深空",
        0xFFF5F5F5.toInt() to "留白",
    )

    val RADIUS_OPTIONS = listOf(0, 8, 16, 28)
    val ROW_OPTIONS = listOf(3, 5, 8, 12)

    /**
     * RemoteViews 里没法直接用带圆角的 shape，只能把渐变+圆角画成位图再塞进 ImageView。
     */
    fun backgroundBitmap(
        widthPx: Int,
        heightPx: Int,
        topColor: Int,
        bottomColor: Int,
        radiusDp: Int,
        density: Float,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(
            widthPx.coerceAtLeast(1),
            heightPx.coerceAtLeast(1),
            Bitmap.Config.ARGB_8888,
        )
        val drawable = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(topColor, bottomColor),
        ).apply {
            cornerRadius = radiusDp * density
        }
        drawable.setBounds(0, 0, bitmap.width, bitmap.height)
        drawable.draw(Canvas(bitmap))
        return bitmap
    }
}
