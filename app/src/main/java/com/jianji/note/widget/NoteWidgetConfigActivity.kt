package com.jianji.note.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jianji.note.App
import com.jianji.note.R
import com.jianji.note.databinding.ActivityWidgetConfigBinding
import com.jianji.note.ui.BaseActivity
import com.jianji.note.util.applySystemBarInsets
import kotlinx.coroutines.launch

/** 添加小组件时的配置页：选文件夹、配色、圆角、显示条数。 */
class NoteWidgetConfigActivity : BaseActivity() {

    private lateinit var binding: ActivityWidgetConfigBinding

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 默认按「取消」返回，只有点了完成才改成 OK
        setResult(RESULT_CANCELED)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        binding = ActivityWidgetConfigBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        applyAppBarTint(binding.appBar)
        setSupportActionBar(binding.toolbar)

        binding.rowFolder.setOnClickListener { pickFolder() }
        binding.rowTop.setOnClickListener { pickColor(top = true) }
        binding.rowBottom.setOnClickListener { pickColor(top = false) }
        binding.rowText.setOnClickListener { pickTextColor() }
        binding.rowRadius.setOnClickListener { pickRadius() }
        binding.rowRows.setOnClickListener { pickRows() }
        binding.btnDone.setOnClickListener { finishConfig() }

        updateSummary()
    }

    private fun updateSummary() {
        lifecycleScope.launch {
            val folderId = WidgetPrefs.folderId(this@NoteWidgetConfigActivity, widgetId)
            binding.tvFolder.text = if (folderId == WidgetPrefs.FOLDER_ALL) {
                getString(R.string.folder_all)
            } else {
                App.repo.folderDao.loadAll().firstOrNull { it.id == folderId }?.name
                    ?: getString(R.string.folder_all)
            }
        }
        binding.tvTop.text = colorName(WidgetPrefs.topColor(this, widgetId))
        binding.tvBottom.text = colorName(WidgetPrefs.bottomColor(this, widgetId))
        binding.tvText.text = getString(
            if (WidgetPrefs.lightText(this, widgetId)) R.string.widget_text_light
            else R.string.widget_text_dark,
        )
        binding.tvRadius.text = radiusName(WidgetPrefs.radius(this, widgetId))
        binding.tvRows.text = WidgetPrefs.rows(this, widgetId).toString()
    }

    private fun colorName(color: Int): String =
        WidgetStyle.COLORS.firstOrNull { it.first == color }?.second ?: "自定义"

    private fun radiusName(radius: Int): String = getString(
        when (radius) {
            0 -> R.string.widget_radius_none
            8 -> R.string.widget_radius_small
            28 -> R.string.widget_radius_large
            else -> R.string.widget_radius_medium
        },
    )

    private fun pickFolder() {
        lifecycleScope.launch {
            val folders = App.repo.folderDao.loadAll()
            val labels = buildList {
                add(getString(R.string.folder_all))
                folders.forEach { add(it.name) }
            }
            MaterialAlertDialogBuilder(this@NoteWidgetConfigActivity)
                .setTitle(R.string.widget_folder)
                .setItems(labels.toTypedArray()) { _, which ->
                    val folderId = if (which == 0) WidgetPrefs.FOLDER_ALL else folders[which - 1].id
                    WidgetPrefs.setFolderId(this@NoteWidgetConfigActivity, widgetId, folderId)
                    updateSummary()
                }
                .show()
        }
    }

    private fun pickColor(top: Boolean) {
        val labels = WidgetStyle.COLORS.map { it.second }.toTypedArray()
        val current = if (top) WidgetPrefs.topColor(this, widgetId)
        else WidgetPrefs.bottomColor(this, widgetId)
        val checked = WidgetStyle.COLORS.indexOfFirst { it.first == current }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (top) R.string.widget_top_color else R.string.widget_bottom_color)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                val color = WidgetStyle.COLORS[which].first
                if (top) WidgetPrefs.setTopColor(this, widgetId, color)
                else WidgetPrefs.setBottomColor(this, widgetId, color)
                dialog.dismiss()
                updateSummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickTextColor() {
        val labels = arrayOf(
            getString(R.string.widget_text_light),
            getString(R.string.widget_text_dark),
        )
        val checked = if (WidgetPrefs.lightText(this, widgetId)) 0 else 1
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widget_text_color)
            .setSingleChoiceItems(labels, checked) { dialog, which ->
                WidgetPrefs.setLightText(this, widgetId, which == 0)
                dialog.dismiss()
                updateSummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickRadius() {
        val labels = WidgetStyle.RADIUS_OPTIONS.map { radiusName(it) }.toTypedArray()
        val current = WidgetPrefs.radius(this, widgetId)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widget_radius)
            .setSingleChoiceItems(labels, WidgetStyle.RADIUS_OPTIONS.indexOf(current)) { d, which ->
                WidgetPrefs.setRadius(this, widgetId, WidgetStyle.RADIUS_OPTIONS[which])
                d.dismiss()
                updateSummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickRows() {
        val labels = WidgetStyle.ROW_OPTIONS.map { "$it 条" }.toTypedArray()
        val current = WidgetPrefs.rows(this, widgetId)
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.widget_rows)
            .setSingleChoiceItems(labels, WidgetStyle.ROW_OPTIONS.indexOf(current)) { d, which ->
                WidgetPrefs.setRows(this, widgetId, WidgetStyle.ROW_OPTIONS[which])
                d.dismiss()
                updateSummary()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun finishConfig() {
        val manager = AppWidgetManager.getInstance(this)
        App.appScope.launch {
            NoteWidgetProvider.render(this@NoteWidgetConfigActivity, manager, widgetId)
        }
        setResult(
            RESULT_OK,
            Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId),
        )
        finish()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }
}
