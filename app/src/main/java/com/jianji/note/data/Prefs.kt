package com.jianji.note.data

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate
import kotlin.math.roundToInt

object Prefs {

    const val THEME_SYSTEM = 0
    const val THEME_LIGHT = 1
    const val THEME_DARK = 2

    const val SORT_UPDATED = 0
    const val SORT_CREATED = 1
    const val SORT_TITLE = 2

    private lateinit var sp: SharedPreferences

    fun init(context: Context) {
        sp = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    }

    var theme: Int
        get() = sp.getInt("theme", THEME_SYSTEM)
        set(value) {
            sp.edit().putInt("theme", value).apply()
            applyNightMode(value)
        }

    var sortBy: Int
        get() = sp.getInt("sort_by", SORT_UPDATED)
        set(value) = sp.edit().putInt("sort_by", value).apply()

    /** 正文字号，sp。 */
    var fontSize: Int
        get() = sp.getInt("font_size", 16)
        set(value) = sp.edit().putInt("font_size", value.coerceIn(12, 30)).apply()

    /** 正文行距倍数。 */
    var lineSpacing: Float
        get() = sp.getFloat("line_spacing", 1.4f)
        set(value) = sp.edit().putFloat("line_spacing", (value * 10).roundToInt() / 10f).apply()

    /** 应用密码，空字符串表示未开启。 */
    var passcode: String
        get() = sp.getString("passcode", "").orEmpty()
        set(value) = sp.edit().putString("passcode", value).apply()

    val passcodeEnabled: Boolean get() = passcode.isNotEmpty()

    /** 进程内是否已解锁；冷启动时重置。 */
    var unlocked: Boolean = false

    fun applyNightMode(value: Int = theme) {
        AppCompatDelegate.setDefaultNightMode(
            when (value) {
                THEME_LIGHT -> AppCompatDelegate.MODE_NIGHT_NO
                THEME_DARK -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            },
        )
    }
}
