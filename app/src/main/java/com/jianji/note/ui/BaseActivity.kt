package com.jianji.note.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat

/**
 * 所有页面统一关闭「系统自动避让系统栏」，改由各页面用
 * [com.jianji.note.util.applySystemBarInsets] 自己处理，
 * 这样在 Android 15+ 的强制边到边行为下表现一致。
 */
abstract class BaseActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
    }
}
