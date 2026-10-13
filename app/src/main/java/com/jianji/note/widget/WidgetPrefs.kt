package com.jianji.note.widget

import android.content.Context

/** 每个小组件实例的独立配置，按 widgetId 分开存。 */
object WidgetPrefs {

    const val FOLDER_ALL = -1L
    const val DEFAULT_TOP = 0xFF3F51B5.toInt()
    const val DEFAULT_BOTTOM = 0xFF5C6BC0.toInt()
    const val DEFAULT_RADIUS = 16
    const val DEFAULT_ROWS = 5

    private const val PREF_NAME = "widgets"

    private fun sp(context: Context) =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private fun key(widgetId: Int, name: String) = "w${widgetId}_$name"

    fun folderId(context: Context, widgetId: Int): Long =
        sp(context).getLong(key(widgetId, "folder"), FOLDER_ALL)

    fun setFolderId(context: Context, widgetId: Int, value: Long) =
        sp(context).edit().putLong(key(widgetId, "folder"), value).apply()

    fun topColor(context: Context, widgetId: Int): Int =
        sp(context).getInt(key(widgetId, "top"), DEFAULT_TOP)

    fun setTopColor(context: Context, widgetId: Int, value: Int) =
        sp(context).edit().putInt(key(widgetId, "top"), value).apply()

    fun bottomColor(context: Context, widgetId: Int): Int =
        sp(context).getInt(key(widgetId, "bottom"), DEFAULT_BOTTOM)

    fun setBottomColor(context: Context, widgetId: Int, value: Int) =
        sp(context).edit().putInt(key(widgetId, "bottom"), value).apply()

    fun lightText(context: Context, widgetId: Int): Boolean =
        sp(context).getBoolean(key(widgetId, "light_text"), true)

    fun setLightText(context: Context, widgetId: Int, value: Boolean) =
        sp(context).edit().putBoolean(key(widgetId, "light_text"), value).apply()

    fun radius(context: Context, widgetId: Int): Int =
        sp(context).getInt(key(widgetId, "radius"), DEFAULT_RADIUS)

    fun setRadius(context: Context, widgetId: Int, value: Int) =
        sp(context).edit().putInt(key(widgetId, "radius"), value).apply()

    fun rows(context: Context, widgetId: Int): Int =
        sp(context).getInt(key(widgetId, "rows"), DEFAULT_ROWS)

    fun setRows(context: Context, widgetId: Int, value: Int) =
        sp(context).edit().putInt(key(widgetId, "rows"), value).apply()

    /** 只清掉这个小组件的键，不动其它实例。 */
    fun clear(context: Context, widgetId: Int) {
        val prefix = "w${widgetId}_"
        val editor = sp(context).edit()
        sp(context).all.keys.filter { it.startsWith(prefix) }.forEach { editor.remove(it) }
        editor.apply()
    }
}
