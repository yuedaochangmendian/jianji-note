package com.jianji.note.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jianji.note.App
import com.jianji.note.R
import com.jianji.note.data.Prefs
import com.jianji.note.databinding.ActivitySettingsBinding
import com.jianji.note.util.Backup
import com.jianji.note.util.AppFiles
import com.jianji.note.util.TimeFmt
import com.jianji.note.util.applySystemBarInsets
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SettingsActivity : BaseActivity() {

    private lateinit var binding: ActivitySettingsBinding

    private val repo get() = App.repo

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri -> uri?.let { exportTo(it) } }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { importFrom(it) } }

    private val setPasscodeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        binding.switchPasscode.isChecked = Prefs.passcodeEnabled
        if (result.resultCode == RESULT_OK) {
            toast(getString(R.string.passcode_enabled_toast))
        }
    }

    private val checkPasscodeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Prefs.passcode = ""
            Prefs.unlocked = true
            toast(getString(R.string.passcode_disabled_toast))
        }
        binding.switchPasscode.isChecked = Prefs.passcodeEnabled
    }

    private val fontPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { pickFont(it) } }

    private val bgMainPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { pickBackground(it, SLOT_MAIN) } }

    private val bgEditorPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { pickBackground(it, SLOT_EDITOR) } }

    private val bgDrawerPicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { pickBackground(it, SLOT_DRAWER) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        applyAppBarTint(binding.appBar)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)

        binding.rowTheme.setOnClickListener { pickTheme() }

        binding.sliderFontSize.value = Prefs.fontSize.toFloat().coerceIn(12f, 30f)
        binding.sliderFontSize.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                Prefs.fontSize = value.toInt()
                updateSummary()
            }
        }

        binding.sliderLineSpacing.value = Prefs.lineSpacing.coerceIn(1.0f, 2.5f)
        binding.sliderLineSpacing.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                Prefs.lineSpacing = value
                updateSummary()
            }
        }

        binding.switchPasscode.isChecked = Prefs.passcodeEnabled
        binding.switchPasscode.setOnCheckedChangeListener { _, checked ->
            if (checked != Prefs.passcodeEnabled) launchPasscode(checked)
        }

        binding.rowExport.setOnClickListener {
            exportLauncher.launch(backupFileName())
        }
        binding.rowImport.setOnClickListener {
            importLauncher.launch(arrayOf("application/json", "text/plain", "*/*"))
        }
        binding.rowClear.setOnClickListener { confirmClear() }
        binding.rowWebdav.setOnClickListener {
            startActivity(Intent(this, WebDavActivity::class.java))
        }

        binding.rowAccent.setOnClickListener { pickAccent() }
        binding.switchTintAppBar.isChecked = Prefs.tintAppBar
        binding.switchTintAppBar.setOnCheckedChangeListener { _, checked ->
            Prefs.tintAppBar = checked
            recreate()
        }
        binding.rowFont.setOnClickListener { fontPicker.launch(arrayOf("font/*", "*/*")) }
        binding.rowBgMain.setOnClickListener { backgroundMenu(SLOT_MAIN) }
        binding.rowBgEditor.setOnClickListener { backgroundMenu(SLOT_EDITOR) }
        binding.rowBgDrawer.setOnClickListener { backgroundMenu(SLOT_DRAWER) }

        binding.tvVersion.text = getString(R.string.about_version, appVersionName())
        updateSummary()
    }

    private fun updateSummary() {
        binding.tvThemeValue.text = getString(
            when (Prefs.theme) {
                Prefs.THEME_LIGHT -> R.string.theme_light
                Prefs.THEME_DARK -> R.string.theme_dark
                else -> R.string.theme_system
            },
        )
        binding.tvFontSizeLabel.text = getString(R.string.settings_font_size, Prefs.fontSize)
        binding.tvLineSpacingLabel.text = getString(
            R.string.settings_line_spacing,
            String.format(Locale.US, "%.1f", Prefs.lineSpacing),
        )
        binding.tvWebdavValue.text = when {
            !Prefs.webdavConfigured -> getString(R.string.webdav_value_off)
            Prefs.webdavLastSync > 0 -> TimeFmt.fullDateTime(Prefs.webdavLastSync)
            else -> getString(R.string.webdav_value_on)
        }
        binding.tvAccentValue.text = accentName(Prefs.accent)
        binding.tvFontValue.text = Prefs.customFont.ifBlank { getString(R.string.value_unset) }
        binding.tvBgMainValue.text = valueLabel(Prefs.backgroundMain)
        binding.tvBgEditorValue.text = valueLabel(Prefs.backgroundEditor)
        binding.tvBgDrawerValue.text = valueLabel(Prefs.backgroundDrawer)
    }

    private fun valueLabel(name: String): String =
        if (name.isBlank()) getString(R.string.value_unset) else getString(R.string.value_set)

    private fun accentName(accent: Int): String = getString(
        when (accent) {
            Prefs.ACCENT_TEAL -> R.string.accent_teal
            Prefs.ACCENT_BLUE -> R.string.accent_blue
            Prefs.ACCENT_PURPLE -> R.string.accent_purple
            Prefs.ACCENT_ORANGE -> R.string.accent_orange
            Prefs.ACCENT_ROSE -> R.string.accent_rose
            else -> R.string.accent_indigo
        },
    )

    private fun pickAccent() {
        val labels = (0 until Prefs.ACCENT_COUNT).map { accentName(it) }.toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_accent)
            .setSingleChoiceItems(labels, Prefs.accent) { dialog, which ->
                Prefs.accent = which
                dialog.dismiss()
                toast(getString(R.string.accent_updated))
                recreate()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun pickFont(uri: Uri) {
        val name = AppFiles.import(this, uri, AppFiles.KIND_FONT)
        if (name == null || !AppFiles.isFontFile(this, name)) {
            name?.let { AppFiles.delete(this, AppFiles.KIND_FONT, it) }
            toast(getString(R.string.font_failed))
            return
        }
        Prefs.customFont = name
        toast(getString(R.string.font_applied, name))
        updateSummary()
        recreate()
    }

    private fun backgroundMenu(slot: Int) {
        MaterialAlertDialogBuilder(this)
            .setItems(
                arrayOf(getString(R.string.bg_pick), getString(R.string.bg_clear)),
            ) { _, which ->
                if (which == 0) {
                    when (slot) {
                        SLOT_EDITOR -> bgEditorPicker.launch(arrayOf("image/*"))
                        SLOT_DRAWER -> bgDrawerPicker.launch(arrayOf("image/*"))
                        else -> bgMainPicker.launch(arrayOf("image/*"))
                    }
                } else {
                    clearBackground(slot)
                }
            }
            .show()
    }

    private fun pickBackground(uri: Uri, slot: Int) {
        val name = AppFiles.import(this, uri, AppFiles.KIND_BACKGROUND)
        if (name == null) {
            toast(getString(R.string.bg_failed))
            return
        }
        when (slot) {
            SLOT_EDITOR -> Prefs.backgroundEditor = name
            SLOT_DRAWER -> Prefs.backgroundDrawer = name
            else -> Prefs.backgroundMain = name
        }
        toast(getString(R.string.bg_applied))
        updateSummary()
        recreate()
    }

    private fun clearBackground(slot: Int) {
        when (slot) {
            SLOT_EDITOR -> Prefs.backgroundEditor = ""
            SLOT_DRAWER -> Prefs.backgroundDrawer = ""
            else -> Prefs.backgroundMain = ""
        }
        toast(getString(R.string.bg_cleared))
        updateSummary()
        recreate()
    }

    private fun pickTheme() {
        val labels = arrayOf(
            getString(R.string.theme_system),
            getString(R.string.theme_light),
            getString(R.string.theme_dark),
        )
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.settings_theme)
            .setSingleChoiceItems(labels, Prefs.theme) { dialog, which ->
                Prefs.theme = which
                updateSummary()
                dialog.dismiss()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun launchPasscode(enable: Boolean) {
        val intent = Intent(this, PasscodeActivity::class.java)
            .putExtra(
                PasscodeActivity.EXTRA_MODE,
                if (enable) PasscodeActivity.MODE_SET else PasscodeActivity.MODE_CHECK,
            )
        if (enable) setPasscodeLauncher.launch(intent) else checkPasscodeLauncher.launch(intent)
    }

    private fun exportTo(uri: Uri) {
        lifecycleScope.launch {
            try {
                val json = Backup.encode(repo.noteDao.loadAll(), repo.folderDao.loadAll())
                contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                toast(getString(R.string.export_success, uri.lastPathSegment.orEmpty()))
            } catch (e: Exception) {
                toast(getString(R.string.export_failed))
            }
        }
    }

    private fun importFrom(uri: Uri) {
        lifecycleScope.launch {
            try {
                val text = contentResolver.openInputStream(uri)
                    ?.bufferedReader()
                    ?.use { it.readText() }
                    ?: return@launch

                val parsed = Backup.decode(text)
                val idMap = HashMap<Long, Long>()
                parsed.folders.forEach { (oldId, folder) ->
                    idMap[oldId] = repo.folderDao.insert(folder)
                }
                val notes = parsed.notes.map { (oldFolderId, note) ->
                    note.copy(folderId = oldFolderId?.let { idMap[it] })
                }
                repo.noteDao.insertAll(notes)
                toast(getString(R.string.import_success, notes.size))
            } catch (e: Exception) {
                toast(getString(R.string.import_failed))
            }
        }
    }

    private fun confirmClear() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.clear_title)
            .setMessage(R.string.clear_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                lifecycleScope.launch {
                    repo.clearAllData()
                    toast(getString(R.string.clear_done))
                }
            }
            .show()
    }

    private fun backupFileName(): String {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmm", Locale.getDefault()).format(Date())
        return "jianji-backup-$stamp.json"
    }

    private fun appVersionName(): String = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(packageName, 0).versionName ?: "1.0.0"
    } catch (e: Exception) {
        "1.0.0"
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private companion object {
        const val SLOT_MAIN = 0
        const val SLOT_EDITOR = 1
        const val SLOT_DRAWER = 2
    }
}
