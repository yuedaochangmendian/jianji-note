package com.jianji.note.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import androidx.lifecycle.lifecycleScope
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jianji.note.App
import com.jianji.note.R
import com.jianji.note.data.Note
import com.jianji.note.data.Prefs
import com.jianji.note.databinding.ActivityEditBinding
import com.jianji.note.util.applySystemBarInsets
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class EditActivity : BaseActivity() {

    private lateinit var binding: ActivityEditBinding

    private val repo get() = App.repo

    private var note: Note? = null
    private var saveJob: Job? = null
    private var dirty = false
    private var loading = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 输入法弹出时也要把内容顶上去，否则正文会被键盘挡住
        binding.root.applySystemBarInsets(bottom = true, includeIme = true)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        supportActionBar?.title = ""

        binding.etContent.textSize = Prefs.fontSize.toFloat()
        binding.etContent.setLineSpacing(0f, Prefs.lineSpacing)
        binding.etTitle.textSize = (Prefs.fontSize + 4).toFloat()

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (loading) return
                dirty = true
                updateCount()
                scheduleSave()
            }
        }
        binding.etTitle.addTextChangedListener(watcher)
        binding.etContent.addTextChangedListener(watcher)

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0L)
        if (noteId == 0L) {
            note = Note()
            binding.etContent.requestFocus()
        } else {
            lifecycleScope.launch {
                note = repo.noteDao.findById(noteId)
                bindNote()
            }
        }
        updateCount()
    }

    private fun bindNote() {
        val current = note ?: return
        loading = true
        binding.etTitle.setText(current.title)
        binding.etContent.setText(current.content)
        loading = false
        invalidateOptionsMenu()
    }

    private fun updateCount() {
        val count = binding.etContent.text?.length ?: 0
        binding.tvCount.text = getString(R.string.editor_count, count)
    }

    /** 输入停止 1.2 秒后落库，避免每次按键都写数据库。 */
    private fun scheduleSave() {
        saveJob?.cancel()
        saveJob = lifecycleScope.launch {
            delay(1200)
            persist()
        }
    }

    private suspend fun persist(force: Boolean = false) {
        val current = note ?: return
        val title = binding.etTitle.text?.toString().orEmpty().trim()
        val content = binding.etContent.text?.toString().orEmpty()
        if (!force) {
            if (!dirty && current.id != 0L) return
            if (title.isEmpty() && content.isBlank()) return
        }

        val updated = current.copy(
            title = title,
            content = content,
            updatedAt = System.currentTimeMillis(),
        )
        note = if (updated.id == 0L) {
            val newId = repo.noteDao.insert(updated)
            updated.copy(id = newId)
        } else {
            repo.noteDao.update(updated)
            updated
        }
        dirty = false
    }

    override fun onPause() {
        super.onPause()
        saveJob?.cancel()
        // 用应用级作用域，避免 Activity 销毁导致最后一次编辑丢失
        App.appScope.launch { persist() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_edit, menu)
        applyPinState(menu)
        return true
    }

    /** 置顶按钮的图标和标题都要反映当前状态，否则点了看不出任何变化。 */
    private fun applyPinState(menu: Menu) {
        val pinned = note?.pinned == true
        menu.findItem(R.id.action_pin)?.apply {
            setIcon(if (pinned) R.drawable.ic_pin else R.drawable.ic_pin_off)
            title = getString(if (pinned) R.string.action_unpin else R.string.action_pin)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_pin -> lifecycleScope.launch {
                val current = note ?: return@launch
                val pinned = !current.pinned
                note = current.copy(pinned = pinned)
                // 新笔记还没落库，这里强制保存一次，否则置顶会随退出丢失
                persist(force = true)
                invalidateOptionsMenu()
                Toast.makeText(
                    this@EditActivity,
                    if (pinned) R.string.toast_pinned else R.string.toast_unpinned,
                    Toast.LENGTH_SHORT,
                ).show()
            }
            R.id.action_move -> moveToFolder()
            R.id.action_share -> shareNote()
            R.id.action_delete -> MaterialAlertDialogBuilder(this)
                .setTitle(R.string.action_delete)
                .setMessage(R.string.editor_delete_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_delete) { _, _ ->
                    lifecycleScope.launch {
                        note?.takeIf { it.id != 0L }?.let {
                            repo.noteDao.moveToTrash(it.id, System.currentTimeMillis())
                        }
                        dirty = false
                        finish()
                    }
                }
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun moveToFolder() {
        lifecycleScope.launch {
            val folders = repo.folderDao.loadAll()
            val labels = buildList {
                add(getString(R.string.folder_uncategorized))
                folders.forEach { add(it.name) }
            }
            MaterialAlertDialogBuilder(this@EditActivity)
                .setTitle(R.string.action_move)
                .setItems(labels.toTypedArray()) { _, index ->
                    val folderId = if (index == 0) null else folders[index - 1].id
                    lifecycleScope.launch {
                        val current = note ?: return@launch
                        persist()
                        val id = note?.id ?: 0L
                        if (id != 0L) repo.noteDao.setFolder(id, folderId)
                        note = (note ?: current).copy(folderId = folderId)
                    }
                }
                .show()
        }
    }

    private fun shareNote() {
        val title = binding.etTitle.text?.toString().orEmpty()
        val content = binding.etContent.text?.toString().orEmpty()
        val text = if (title.isBlank()) content else "$title\n\n$content"
        if (text.isBlank()) return
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_TEXT, text)
        }
        startActivity(android.content.Intent.createChooser(intent, getString(R.string.action_share)))
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }
}
