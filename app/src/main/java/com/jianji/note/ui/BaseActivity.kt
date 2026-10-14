package com.jianji.note.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.color.MaterialColors
import com.jianji.note.R
import com.jianji.note.data.Prefs

/**
 * 公共的窗口与样式处理：
 * - 关闭系统自动避让，由各页面自己用 insets 处理（Android 15+ 强制边到边）
 * - 把「强调色」叠加到当前主题上
 * - 状态栏图标明暗
 * - 「应用栏用强调色」开关
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // 必须在 setContentView 之前叠加，否则视图已经按旧主题创建好了
        theme.applyStyle(UiStyle.accentOverlayRes(Prefs.accent), true)
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        updateStatusBarIcons()
    }

    protected fun isNightMode(): Boolean =
        (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
            Configuration.UI_MODE_NIGHT_YES

    protected fun currentAccent(): Int =
        MaterialColors.getColor(window.decorView, R.attr.jianjiAccent)

    protected fun updateStatusBarIcons() {
        // 只管状态栏；底部导航栏交给系统与主题自己处理
        val lightIcons = !isNightMode() && !Prefs.tintAppBar
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = lightIcons
    }

    /**
     * 开启「应用栏使用强调色」后，AppBarLayout 直接铺强调色。
     * Android 15 起状态栏背景被系统强制透明，没法再单独设置颜色；
     * 让内容画到状态栏下面、由应用栏自己延伸上去，是当前唯一能做出
     * 「状态栏着色」效果的办法。
     */
    protected fun applyAppBarTint(appBar: View?) {
        if (!Prefs.tintAppBar || appBar == null) return
        appBar.setBackgroundColor(currentAccent())
        WindowInsetsControllerCompat(window, window.decorView)
            .isAppearanceLightStatusBars = false
    }
}
