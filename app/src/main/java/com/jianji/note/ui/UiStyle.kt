package com.jianji.note.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.view.View
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import com.jianji.note.R
import com.jianji.note.data.Prefs
import com.jianji.note.util.AppFiles

/** 强调色、自定义字体、自定义背景的应用逻辑。 */
object UiStyle {

    fun accentOverlayRes(accent: Int): Int = when (accent) {
        Prefs.ACCENT_TEAL -> R.style.ThemeOverlay_Jianji_Accent1
        Prefs.ACCENT_BLUE -> R.style.ThemeOverlay_Jianji_Accent2
        Prefs.ACCENT_PURPLE -> R.style.ThemeOverlay_Jianji_Accent3
        Prefs.ACCENT_ORANGE -> R.style.ThemeOverlay_Jianji_Accent4
        Prefs.ACCENT_ROSE -> R.style.ThemeOverlay_Jianji_Accent5
        else -> R.style.ThemeOverlay_Jianji_Accent0
    }

    /** 用户选的字体文件；没设置或加载失败时返回 null，表示用系统字体。 */
    fun typeface(context: Context): Typeface? {
        val name = Prefs.customFont
        if (name.isBlank()) return null
        return runCatching {
            Typeface.createFromFile(AppFiles.file(context, AppFiles.KIND_FONT, name))
        }.getOrNull()
    }

    /**
     * 给视图铺背景图，并在上面盖一层半透明遮罩，
     * 否则图片一花正文就看不清了。没有设置背景时用主题原色。
     */
    fun applyBackground(view: View, @ColorRes fallback: Int, imageName: String) {
        val image = loadBackground(view.context, imageName)
        if (image == null) {
            view.setBackgroundColor(ContextCompat.getColor(view.context, fallback))
            return
        }
        val scrim = ColorDrawable(ContextCompat.getColor(view.context, R.color.bg_scrim))
        view.background = LayerDrawable(arrayOf(image, scrim))
    }

    fun loadBackground(context: Context, name: String): Drawable? {
        if (name.isBlank()) return null
        val file = AppFiles.file(context, AppFiles.KIND_BACKGROUND, name)
        if (!file.exists()) return null
        return runCatching { Drawable.createFromPath(file.absolutePath) }.getOrNull()
    }
}
