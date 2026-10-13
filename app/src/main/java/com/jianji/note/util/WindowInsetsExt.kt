package com.jianji.note.util

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

/**
 * targetSdk 35 起 Android 15 会强制边到边显示，内容会画到状态栏和导航栏下面，
 * 因此每个页面的根布局都要自己把系统栏的高度让出来。
 *
 * 以调用时的 padding 为基准叠加，避免 insets 多次回调导致 padding 累积。
 */
fun View.applySystemBarInsets(
    top: Boolean = true,
    bottom: Boolean = true,
    horizontal: Boolean = false,
    includeIme: Boolean = false,
) {
    val baseLeft = paddingLeft
    val baseTop = paddingTop
    val baseRight = paddingRight
    val baseBottom = paddingBottom

    ViewCompat.setOnApplyWindowInsetsListener(this) { view, windowInsets ->
        val bars = windowInsets.getInsets(
            WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout(),
        )
        val imeBottom = if (includeIme) {
            windowInsets.getInsets(WindowInsetsCompat.Type.ime()).bottom
        } else {
            0
        }
        view.setPadding(
            baseLeft + if (horizontal) bars.left else 0,
            baseTop + if (top) bars.top else 0,
            baseRight + if (horizontal) bars.right else 0,
            baseBottom + if (bottom) maxOf(bars.bottom, imeBottom) else 0,
        )
        windowInsets
    }
}
