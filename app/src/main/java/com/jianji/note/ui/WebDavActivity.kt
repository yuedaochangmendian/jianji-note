package com.jianji.note.ui

import android.os.Bundle
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jianji.note.App
import com.jianji.note.R
import com.jianji.note.data.Prefs
import com.jianji.note.databinding.ActivityWebdavBinding
import com.jianji.note.net.WebDavParser
import com.jianji.note.net.WebDavResult
import com.jianji.note.net.WebDavSync
import com.jianji.note.util.TimeFmt
import com.jianji.note.util.applySystemBarInsets
import kotlinx.coroutines.launch
import java.util.Locale

/** WebDAV 云同步：配置连接、上传快照、选择云端备份恢复。 */
class WebDavActivity : BaseActivity() {

    private lateinit var binding: ActivityWebdavBinding

    private val repo get() = App.repo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityWebdavBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets(bottom = true, includeIme = true)
        applyAppBarTint(binding.appBar)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)

        binding.etUrl.setText(Prefs.webdavUrl)
        binding.etUser.setText(Prefs.webdavUser)
        binding.etPassword.setText(Prefs.webdavPassword)
        binding.etFileName.setText(WebDavSync.fileName())
        binding.switchAuto.isChecked = Prefs.webdavAutoBackup

        binding.switchAuto.setOnCheckedChangeListener { _, checked ->
            if (checked && !Prefs.webdavConfigured) {
                toast(getString(R.string.webdav_not_configured))
                binding.switchAuto.isChecked = false
                return@setOnCheckedChangeListener
            }
            Prefs.webdavAutoBackup = checked
        }

        binding.btnSave.setOnClickListener {
            saveConfig()
            binding.tvStatus.text = getString(R.string.webdav_saved)
            toast(getString(R.string.webdav_saved))
        }

        binding.btnTest.setOnClickListener {
            saveConfig()
            runSync(
                running = getString(R.string.webdav_testing),
                block = {
                    WebDavSync.clientOrNull()?.testConnection()
                        ?: WebDavResult.Error(getString(R.string.webdav_not_configured))
                },
            ) {
                binding.tvStatus.text = getString(R.string.webdav_test_ok)
                toast(getString(R.string.webdav_test_ok))
            }
        }

        binding.btnUpload.setOnClickListener {
            saveConfig()
            runSync(
                running = getString(R.string.webdav_uploading),
                block = { WebDavSync.upload(repo) },
            ) {
                binding.tvStatus.text = getString(R.string.webdav_upload_ok)
                toast(getString(R.string.webdav_upload_ok))
            }
        }

        binding.btnRestore.setOnClickListener {
            saveConfig()
            runSync(
                running = getString(R.string.webdav_loading),
                block = { WebDavSync.listBackups() },
            ) { files -> pickBackup(files) }
        }

        showLastSync()
    }

    private fun showLastSync() {
        binding.tvStatus.text = if (Prefs.webdavLastSync > 0) {
            getString(
                R.string.webdav_last_sync,
                TimeFmt.fullDateTime(Prefs.webdavLastSync),
                Prefs.webdavLastResult,
            )
        } else {
            ""
        }
    }

    private fun saveConfig() {
        Prefs.webdavUrl = binding.etUrl.text?.toString().orEmpty()
        Prefs.webdavUser = binding.etUser.text?.toString().orEmpty()
        Prefs.webdavPassword = binding.etPassword.text?.toString().orEmpty()
        Prefs.webdavFileName = binding.etFileName.text?.toString().orEmpty()
            .ifBlank { WebDavSync.DEFAULT_FILE_NAME }
    }

    private fun <T> runSync(
        running: String,
        block: suspend () -> WebDavResult<T>,
        onSuccess: (T) -> Unit,
    ) {
        if (!Prefs.webdavConfigured) {
            toast(getString(R.string.webdav_not_configured))
            return
        }
        setBusy(true)
        binding.tvStatus.text = running
        lifecycleScope.launch {
            val result = try {
                block()
            } catch (e: Exception) {
                WebDavResult.Error(e.message ?: "未知错误")
            }
            setBusy(false)
            when (result) {
                is WebDavResult.Success -> onSuccess(result.data)
                is WebDavResult.Error -> {
                    binding.tvStatus.text = result.message
                    toast(result.message)
                }
            }
        }
    }

    private fun setBusy(busy: Boolean) {
        binding.progress.visibility = if (busy) View.VISIBLE else View.GONE
        binding.btnTest.isEnabled = !busy
        binding.btnSave.isEnabled = !busy
        binding.btnUpload.isEnabled = !busy
        binding.btnRestore.isEnabled = !busy
    }

    private fun pickBackup(files: List<WebDavParser.RemoteEntry>) {
        if (files.isEmpty()) {
            binding.tvStatus.text = getString(R.string.webdav_no_files)
            toast(getString(R.string.webdav_no_files))
            return
        }
        val labels = files.map(::describe).toTypedArray()
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.webdav_pick_file)
            .setItems(labels) { _, which -> confirmRestore(files[which]) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirmRestore(entry: WebDavParser.RemoteEntry) {
        lifecycleScope.launch {
            val localCount = repo.noteDao.loadAll().size
            MaterialAlertDialogBuilder(this@WebDavActivity)
                .setTitle(R.string.webdav_restore_confirm_title)
                .setMessage(getString(R.string.webdav_restore_confirm_message, localCount))
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_confirm) { _, _ -> restore(entry) }
                .show()
        }
    }

    private fun restore(entry: WebDavParser.RemoteEntry) {
        runSync(
            running = getString(R.string.webdav_restoring),
            block = { WebDavSync.restore(repo, entry.name) },
        ) { count ->
            binding.tvStatus.text = getString(R.string.webdav_restore_ok, count)
            toast(getString(R.string.webdav_restore_ok, count))
        }
    }

    private fun describe(entry: WebDavParser.RemoteEntry): String {
        val size = when {
            entry.size < 0 -> ""
            entry.size < 1024 -> "${entry.size} B"
            entry.size < 1024 * 1024 -> "${entry.size / 1024} KB"
            else -> String.format(Locale.US, "%.1f MB", entry.size / 1024.0 / 1024.0)
        }
        val time = entry.lastModified?.let { TimeFmt.fullDateTime(it) }.orEmpty()
        return listOf(entry.name, size, time).filter { it.isNotBlank() }.joinToString("  ·  ")
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
}
