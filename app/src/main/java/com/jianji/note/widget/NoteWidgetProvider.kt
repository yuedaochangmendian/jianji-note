package com.jianji.note.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.view.View
import android.widget.RemoteViews
import com.jianji.note.App
import com.jianji.note.R
import com.jianji.note.data.Note
import com.jianji.note.data.NoteSort
import com.jianji.note.data.Prefs
import com.jianji.note.ui.EditActivity
import com.jianji.note.ui.MainActivity
import com.jianji.note.util.TimeFmt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 桌面小组件：显示某个文件夹里的笔记列表。
 *
 * RemoteViews 不支持任意控件，列表是用 addView 往 LinearLayout 里塞行做的，
 * 每一行单独挂 PendingIntent 打开对应笔记。
 */
class NoteWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        // 要读数据库，不能在广播里同步做，用 goAsync 把生命周期撑住
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                appWidgetIds.forEach { render(context, appWidgetManager, it) }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        appWidgetIds.forEach { WidgetPrefs.clear(context, it) }
    }

    companion object {

        /** 数据变了之后刷新所有组件实例。 */
        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context) ?: return
            val ids = manager.getAppWidgetIds(
                ComponentName(context, NoteWidgetProvider::class.java),
            )
            if (ids.isEmpty()) return
            App.appScope.launch { ids.forEach { render(context, manager, it) } }
        }

        suspend fun render(context: Context, manager: AppWidgetManager, widgetId: Int) {
            val views = RemoteViews(context.packageName, R.layout.widget_note)
            val density = context.resources.displayMetrics.density
            val options = manager.getAppWidgetOptions(widgetId)
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, 180)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 120)

            val topColor = WidgetPrefs.topColor(context, widgetId)
            val bottomColor = WidgetPrefs.bottomColor(context, widgetId)
            val radius = WidgetPrefs.radius(context, widgetId)
            val lightText = WidgetPrefs.lightText(context, widgetId)
            val maxRows = WidgetPrefs.rows(context, widgetId)
            val folderId = WidgetPrefs.folderId(context, widgetId)

            val textColor = if (lightText) 0xFFFFFFFF.toInt() else 0xFF212121.toInt()
            val subTextColor = if (lightText) 0xCCFFFFFF.toInt() else 0x99000000.toInt()

            views.setImageViewBitmap(
                R.id.widgetBackground,
                WidgetStyle.backgroundBitmap(
                    (widthDp * density).toInt(),
                    (heightDp * density).toInt(),
                    topColor,
                    bottomColor,
                    radius,
                    density,
                ),
            )

            val allNotes = App.repo.noteDao.loadAll().filter { it.deletedAt == null }
            val notes = if (folderId == WidgetPrefs.FOLDER_ALL) {
                allNotes
            } else {
                allNotes.filter { it.folderId == folderId }
            }
            val shown = NoteSort.sort(notes, Prefs.sortBy).take(maxRows)

            val folderName = if (folderId == WidgetPrefs.FOLDER_ALL) {
                context.getString(R.string.folder_all)
            } else {
                App.repo.folderDao.loadAll().firstOrNull { it.id == folderId }?.name
                    ?: context.getString(R.string.folder_all)
            }

            views.setTextViewText(R.id.widgetTitle, folderName)
            views.setTextColor(R.id.widgetTitle, textColor)
            views.setOnClickPendingIntent(R.id.widgetHeader, openAppPendingIntent(context, widgetId))
            views.setOnClickPendingIntent(R.id.widgetAdd, newNotePendingIntent(context, widgetId))

            views.removeAllViews(R.id.widgetList)
            if (shown.isEmpty()) {
                views.setViewVisibility(R.id.widgetEmpty, View.VISIBLE)
                views.setTextColor(R.id.widgetEmpty, subTextColor)
            } else {
                views.setViewVisibility(R.id.widgetEmpty, View.GONE)
                shown.forEachIndexed { index, note ->
                    views.addView(R.id.widgetList, buildRow(context, widgetId, index, note, textColor, subTextColor))
                }
            }

            manager.updateAppWidget(widgetId, views)
        }

        private fun buildRow(
            context: Context,
            widgetId: Int,
            index: Int,
            note: Note,
            textColor: Int,
            subTextColor: Int,
        ): RemoteViews {
            val row = RemoteViews(context.packageName, R.layout.widget_note_row)
            row.setTextViewText(R.id.rowTitle, note.displayTitle)
            row.setTextViewText(R.id.rowTime, TimeFmt.friendly(note.updatedAt))
            row.setTextColor(R.id.rowTitle, textColor)
            row.setTextColor(R.id.rowTime, subTextColor)
            row.setOnClickPendingIntent(
                R.id.rowRoot,
                PendingIntent.getActivity(
                    context,
                    widgetId * 100 + index,
                    Intent(context, EditActivity::class.java)
                        .putExtra(EditActivity.EXTRA_NOTE_ID, note.id),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            return row
        }

        private fun openAppPendingIntent(context: Context, widgetId: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                widgetId,
                Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        private fun newNotePendingIntent(context: Context, widgetId: Int): PendingIntent =
            PendingIntent.getActivity(
                context,
                widgetId + 100000,
                Intent(context, MainActivity::class.java).setAction(ACTION_NEW_NOTE),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
    }
}

const val ACTION_NEW_NOTE = "com.jianji.note.action.NEW_NOTE"
const val ACTION_SEARCH = "com.jianji.note.action.SEARCH"
