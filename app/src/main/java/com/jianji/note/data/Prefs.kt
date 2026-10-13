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

    const val LAYOUT_NORMAL = 0
    const val LAYOUT_CARD = 1
    const val LAYOUT_GRID = 2
    const val LAYOUT_SUMMARY = 3
    const val LAYOUT_DAILY = 4
    const val LAYOUT_BRIEF = 5
    const val LAYOUT_MD = 6
    const val LAYOUT_MI_NOTE = 7
    const val LAYOUT_SMARTISAN = 8
    const val LAYOUT_BEAR = 9

    const val ACCENT_INDIGO = 0
    const val ACCENT_TEAL = 1
    const val ACCENT_BLUE = 2
    const val ACCENT_PURPLE = 3
    const val ACCENT_ORANGE = 4
    const val ACCENT_ROSE = 5

    val ACCENT_COUNT = 6

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

    /** 列表样式，取值见 LAYOUT_*。 */
    var layoutType: Int
        get() = sp.getInt("layout_type", LAYOUT_NORMAL)
        set(value) = sp.edit().putInt("layout_type", value).apply()

    /** 强调色编号，见 ACCENT_*。 */
    var accent: Int
        get() = sp.getInt("accent", ACCENT_INDIGO)
        set(value) = sp.edit().putInt("accent", value.coerceIn(0, ACCENT_COUNT - 1)).apply()

    /** 开启后应用栏用强调色，状态栏区域也就跟着染色了。 */
    var tintAppBar: Boolean
        get() = sp.getBoolean("tint_app_bar", false)
        set(value) = sp.edit().putBoolean("tint_app_bar", value).apply()

    /** 自定义字体文件名（存在 filesDir/fonts），空表示用系统字体。 */
    var customFont: String
        get() = sp.getString("custom_font", "").orEmpty()
        set(value) = sp.edit().putString("custom_font", value).apply()

    /** 各页面自定义背景的文件名（存在 filesDir/backgrounds），空表示用主题底色。 */
    var backgroundMain: String
        get() = sp.getString("bg_main", "").orEmpty()
        set(value) = sp.edit().putString("bg_main", value).apply()

    var backgroundEditor: String
        get() = sp.getString("bg_editor", "").orEmpty()
        set(value) = sp.edit().putString("bg_editor", value).apply()

    var backgroundDrawer: String
        get() = sp.getString("bg_drawer", "").orEmpty()
        set(value) = sp.edit().putString("bg_drawer", value).apply()

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

    // ---- WebDAV 云同步 ----

    /** 目录地址，约定以 / 结尾。 */
    var webdavUrl: String
        get() = sp.getString("webdav_url", "").orEmpty()
        set(value) = sp.edit().putString("webdav_url", value.trim()).apply()

    var webdavUser: String
        get() = sp.getString("webdav_user", "").orEmpty()
        set(value) = sp.edit().putString("webdav_user", value.trim()).apply()

    /** 注意：与密码锁一样是明文存储，只做本机便于使用的存储，不做加密。 */
    var webdavPassword: String
        get() = sp.getString("webdav_password", "").orEmpty()
        set(value) = sp.edit().putString("webdav_password", value).apply()

    var webdavFileName: String
        get() = sp.getString("webdav_file", "").orEmpty()
        set(value) = sp.edit().putString("webdav_file", value.trim()).apply()

    var webdavAutoBackup: Boolean
        get() = sp.getBoolean("webdav_auto", false)
        set(value) = sp.edit().putBoolean("webdav_auto", value).apply()

    var webdavLastSync: Long
        get() = sp.getLong("webdav_last_sync", 0L)
        set(value) = sp.edit().putLong("webdav_last_sync", value).apply()

    var webdavLastResult: String
        get() = sp.getString("webdav_last_result", "").orEmpty()
        set(value) = sp.edit().putString("webdav_last_result", value).apply()

    val webdavConfigured: Boolean
        get() = webdavUrl.isNotBlank() && webdavUser.isNotBlank()

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
