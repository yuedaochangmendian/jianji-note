package com.jianji.note.util

import android.view.View

/**
 * 全应用统一的出现/消失动画：180ms 淡入淡出 + 一点纵向位移。
 * 之前工具栏、图片条是直接切 visibility，显得很生硬。
 */
object ViewAnim {

    const val DURATION = 180L

    fun show(view: View) {
        view.animate().cancel()
        view.visibility = View.VISIBLE
        view.alpha = 0f
        view.translationY = offset(view)
        view.animate().alpha(1f).translationY(0f).setDuration(DURATION).start()
    }

    fun hide(view: View) {
        if (view.visibility != View.VISIBLE) return
        view.animate().cancel()
        view.animate()
            .alpha(0f)
            .translationY(offset(view))
            .setDuration(DURATION)
            .withEndAction {
                view.visibility = View.GONE
                view.alpha = 1f
                view.translationY = 0f
            }
            .start()
    }

    /** 「容器还在但内容换了」的场景（切 Markdown 模式等），淡入提示变化。 */
    fun fadeIn(view: View) {
        view.animate().cancel()
        view.alpha = 0f
        view.animate().alpha(1f).setDuration(DURATION).start()
    }

    private fun offset(view: View): Float = 12f * view.resources.displayMetrics.density
}
