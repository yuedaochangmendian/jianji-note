package com.jianji.note.ui

import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.TextView
import android.widget.Toast
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import androidx.core.content.ContextCompat
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.jianji.note.App
import com.jianji.note.R
import com.jianji.note.data.Note
import com.jianji.note.data.Prefs
import com.jianji.note.databinding.ActivityEditBinding
import com.jianji.note.util.EditorTools
import com.jianji.note.util.AppFiles
import com.jianji.note.util.Exporter
import com.jianji.note.util.ImageList
import com.jianji.note.util.ImageLoader
import com.jianji.note.util.applySystemBarInsets
import java.io.File
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditActivity : BaseActivity() {

    private lateinit var binding: ActivityEditBinding

    private val repo get() = App.repo

    private var note: Note? = null
    private var saveJob: Job? = null
    private var snapshotJob: Job? = null
    private var dirty = false
    private var loading = false

    private data class Snapshot(val title: String, val content: String)

    /** 工具栏条目：要么是一个文本操作，要么是「插入图片」。 */
    private data class ToolEntry(
        val label: String,
        val tool: EditorTools.Tool? = null,
        val insertImage: Boolean = false,
    )

    private val undoStack = ArrayDeque<Snapshot>()
    private val redoStack = ArrayDeque<Snapshot>()
    private var lastCommitted: Snapshot? = null

    private val imagePicker = registerForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let { addImage(it) } }

    private val exportTxtLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> uri?.let { exportTo(it, EXPORT_TXT) } }

    private val exportMdLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> uri?.let { exportTo(it, EXPORT_MD) } }

    private val exportPdfLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/pdf"),
    ) { uri -> uri?.let { exportTo(it, EXPORT_PDF) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 输入法弹出时也要把内容顶上去，否则正文会被键盘挡住
        binding.root.applySystemBarInsets(bottom = true, includeIme = true)
        applyAppBarTint(binding.appBar)
        UiStyle.applyBackground(binding.root, R.color.bg_fallback_surface, Prefs.backgroundEditor)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)
        supportActionBar?.title = ""

        applyTextStyle()

        val watcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            override fun afterTextChanged(s: Editable?) {
                if (loading) return
                onEdited()
            }
        }
        binding.etTitle.addTextChangedListener(watcher)
        binding.etContent.addTextChangedListener(watcher)

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0L)
        if (noteId == 0L) {
            note = Note()
            lastCommitted = currentSnapshot()
            binding.etContent.requestFocus()
            refreshBottom()
        } else {
            lifecycleScope.launch {
                note = repo.noteDao.findById(noteId)
                bindNote()
                refreshBottom()
            }
        }
    }

    private fun applyTextStyle() {
        binding.etContent.textSize = Prefs.fontSize.toFloat()
        binding.etContent.setLineSpacing(0f, Prefs.lineSpacing)
        binding.etTitle.textSize = (Prefs.fontSize + 4).toFloat()
        UiStyle.typeface(this)?.let { face ->
            binding.etContent.typeface = face
            binding.etTitle.typeface = face
        }
    }

    private fun bindNote() {
        val current = note ?: return
        loading = true
        binding.etTitle.setText(current.title)
        binding.etContent.setText(current.content)
        loading = false
        undoStack.clear()
        redoStack.clear()
        lastCommitted = currentSnapshot()
        invalidateOptionsMenu()
    }

    private fun onEdited() {
        dirty = true
        updateCount()
        scheduleSave()
        scheduleSnapshot()
    }

    private fun updateCount() {
        val count = binding.etContent.text?.length ?: 0
        val images = ImageList.decode(note?.images).size
        binding.tvCount.text = if (images > 0) {
            getString(R.string.editor_count_with_images, count, images)
        } else {
            getString(R.string.editor_count, count)
        }
    }

    private fun refreshBottom() {
        updateCount()
        buildToolBar()
        refreshImageStrip()
    }

    // ---------------- 图片附件 ----------------

    private fun addImage(uri: Uri) {
        lifecycleScope.launch {
            // 复制文件是阻塞 IO，别放主线程
            val name = withContext(Dispatchers.IO) {
                AppFiles.import(this@EditActivity, uri, AppFiles.KIND_IMAGE)
            }
            if (name == null) {
                toast(getString(R.string.image_failed))
                return@launch
            }
            val current = note ?: Note()
            note = current.copy(images = ImageList.append(current.images, name))

            // 正文里留一个占位符，预览时替换成真正的图片
            val edit = binding.etContent
            val at = edit.selectionStart.coerceAtLeast(0)
            val text = edit.text?.toString().orEmpty()
            val marker = "[img]"
            val newText = text.substring(0, at) + marker + text.substring(at)
            loading = true
            edit.setText(newText)
            edit.setSelection((at + marker.length).coerceAtMost(newText.length))
            loading = false
            dirty = true

            persist(force = true)
            refreshBottom()
            toast(getString(R.string.image_added))
        }
    }

    private fun refreshImageStrip() {
        val names = ImageList.decode(note?.images)
        binding.imageStrip.visibility = if (names.isEmpty()) View.GONE else View.VISIBLE
        binding.imageRow.removeAllViews()
        val size = dp(56)
        names.forEach { name ->
            val thumb = ImageView(this).apply {
                layoutParams = LinearLayout.LayoutParams(size, size).apply { marginEnd = dp(8) }
                scaleType = ImageView.ScaleType.CENTER_CROP
                setOnClickListener { confirmRemoveImage(name) }
            }
            ImageLoader.loadThumbnail(thumb, AppFiles.file(this, AppFiles.KIND_IMAGE, name), size)
            binding.imageRow.addView(thumb)
        }
    }

    private fun confirmRemoveImage(name: String) {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.image_remove_title)
            .setMessage(R.string.image_remove_message)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                lifecycleScope.launch {
                    note = note?.copy(images = ImageList.remove(note?.images, name))
                    AppFiles.delete(this@EditActivity, AppFiles.KIND_IMAGE, name)
                    persist(force = true)
                    refreshBottom()
                }
            }
            .show()
    }

    // ---------------- 导出与分享 ----------------

    private fun exportTo(uri: Uri, kind: Int) {
        lifecycleScope.launch {
            saveJob?.cancel()
            persist(force = true)
            val current = note ?: return@launch
            val ok = withContext(Dispatchers.IO) {
                try {
                    when (kind) {
                        EXPORT_PDF -> {
                            val temp = File(cacheDir, "jianji-export.pdf")
                            val written = PdfExporter.write(temp, current, Prefs.fontSize.toFloat())
                            if (written) {
                                contentResolver.openOutputStream(uri)?.use { output ->
                                    temp.inputStream().use { it.copyTo(output) }
                                }
                            }
                            written
                        }

                        EXPORT_MD -> writeText(uri, Exporter.toMarkdown(current))
                        else -> writeText(uri, Exporter.toPlainText(current))
                    }
                } catch (e: Exception) {
                    false
                }
            }
            if (ok) {
                toast(getString(R.string.export_success_named, uri.lastPathSegment.orEmpty()))
            } else {
                toast(getString(R.string.export_pdf_failed))
            }
        }
    }

    private fun writeText(uri: Uri, text: String): Boolean = try {
        contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
        true
    } catch (e: Exception) {
        false
    }

    private fun shareAsImage() {
        lifecycleScope.launch {
            saveJob?.cancel()
            persist(force = true)
            val current = note ?: return@launch
            val outputFile = withContext(Dispatchers.IO) {
                runCatching {
                    val bitmap = ShareImageRenderer.render(this@EditActivity, current)
                    val file = File(File(cacheDir, "shared").apply { mkdirs() }, "jianji-share.png")
                    file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                    file
                }.getOrNull()
            }
            if (outputFile == null) {
                toast(getString(R.string.share_image_failed))
                return@launch
            }
            val uri = FileProvider.getUriForFile(
                this@EditActivity, "$packageName.fileprovider", outputFile,
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, getString(R.string.share_as_image)))
        }
    }

    private fun currentSnapshot() = Snapshot(
        binding.etTitle.text?.toString().orEmpty(),
        binding.etContent.text?.toString().orEmpty(),
    )

    /** 输入停顿 700ms 才记一次历史，避免每敲一个字都入栈。 */
    private fun scheduleSnapshot() {
        snapshotJob?.cancel()
        snapshotJob = lifecycleScope.launch {
            delay(700)
            commitSnapshot()
        }
    }

    private fun commitSnapshot() {
        val current = currentSnapshot()
        if (current == lastCommitted) return
        lastCommitted?.let { undoStack.addLast(it) }
        while (undoStack.size > MAX_HISTORY) undoStack.removeFirst()
        redoStack.clear()
        lastCommitted = current
    }

    private fun undo() {
        commitSnapshot()
        val previous = undoStack.removeLastOrNull()
        if (previous == null) {
            toast(getString(R.string.toast_undo_empty))
            return
        }
        lastCommitted?.let { redoStack.addLast(it) }
        applySnapshot(previous)
        lastCommitted = previous
    }

    private fun redo() {
        commitSnapshot()
        val next = redoStack.removeLastOrNull()
        if (next == null) {
            toast(getString(R.string.toast_redo_empty))
            return
        }
        lastCommitted?.let { undoStack.addLast(it) }
        applySnapshot(next)
        lastCommitted = next
    }

    private fun applySnapshot(snapshot: Snapshot) {
        loading = true
        binding.etTitle.setText(snapshot.title)
        binding.etContent.setText(snapshot.content)
        loading = false
        dirty = true
        binding.etContent.setSelection(binding.etContent.text?.length ?: 0)
        updateCount()
        scheduleSave()
    }

    private fun buildToolBar() {
        val markdown = note?.markdown == true
        binding.toolScroll.visibility = View.VISIBLE
        binding.toolBar.removeAllViews()

        val color = ContextCompat.getColor(this, R.color.tool_text)
        val ripple = resolveSelectableItemBackground()
        val tools = if (markdown) MARKDOWN_TOOLS else NORMAL_TOOLS

        tools.forEach { entry ->
            val button = TextView(this).apply {
                text = entry.label
                textSize = 14f
                gravity = Gravity.CENTER
                setTextColor(color)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                if (ripple != 0) setBackgroundResource(ripple)
                setOnClickListener {
                    if (entry.insertImage) {
                        imagePicker.launch(arrayOf("image/*"))
                    } else {
                        entry.tool?.let { applyTool(it) }
                    }
                }
            }
            binding.toolBar.addView(button)
        }
    }

    private fun applyTool(tool: EditorTools.Tool) {
        val edit = binding.etContent
        val start = edit.selectionStart.coerceAtLeast(0)
        val end = edit.selectionEnd.coerceAtLeast(start)
        val result = EditorTools.apply(
            source = edit.text?.toString().orEmpty(),
            start = start,
            end = end,
            tool = tool,
            now = System.currentTimeMillis(),
        )
        loading = true
        edit.setText(result.text)
        edit.setSelection(result.selection.coerceIn(0, result.text.length))
        loading = false
        dirty = true
        updateCount()
        scheduleSave()
        scheduleSnapshot()
        edit.requestFocus()
    }

    private fun resolveSelectableItemBackground(): Int {
        val value = TypedValue()
        theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, value, true)
        return value.resourceId
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

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
        applyMenuState(menu)
        return true
    }

    private fun applyMenuState(menu: Menu) {
        val pinned = note?.pinned == true
        menu.findItem(R.id.action_pin)?.apply {
            setIcon(if (pinned) R.drawable.ic_pin else R.drawable.ic_pin_off)
            title = getString(if (pinned) R.string.action_unpin else R.string.action_pin)
        }
        menu.findItem(R.id.action_lock)?.title = getString(
            if (note?.locked == true) R.string.action_unlock else R.string.action_lock,
        )
        menu.findItem(R.id.action_markdown)?.title = getString(
            if (note?.markdown == true) R.string.editor_switch_to_normal
            else R.string.editor_switch_to_markdown,
        )
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> {
                finish()
                return true
            }
            R.id.action_undo -> undo()
            R.id.action_redo -> redo()
            R.id.action_preview -> preview()
            R.id.action_markdown -> toggleMarkdown()
            R.id.action_pin -> togglePin()
            R.id.action_lock -> toggleLock()
            R.id.action_move -> moveToFolder()
            R.id.action_share -> shareNote()
            R.id.action_share_image -> shareAsImage()
            R.id.export_txt -> exportTxtLauncher.launch(Exporter.fileNameFor(note ?: Note(), "txt"))
            R.id.export_md -> exportMdLauncher.launch(Exporter.fileNameFor(note ?: Note(), "md"))
            R.id.export_pdf -> exportPdfLauncher.launch(Exporter.fileNameFor(note ?: Note(), "pdf"))
            R.id.action_delete -> confirmDelete()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun togglePin() {
        lifecycleScope.launch {
            val current = note ?: return@launch
            val pinned = !current.pinned
            note = current.copy(pinned = pinned)
            // 新笔记还没落库，这里强制保存一次，否则置顶会随退出丢失
            persist(force = true)
            invalidateOptionsMenu()
            toast(getString(if (pinned) R.string.toast_pinned else R.string.toast_unpinned))
        }
    }

    private fun toggleLock() {
        if (!Prefs.passcodeEnabled) {
            toast(getString(R.string.toast_need_passcode_first))
            return
        }
        lifecycleScope.launch {
            val current = note ?: return@launch
            val locked = !current.locked
            note = current.copy(locked = locked)
            persist(force = true)
            invalidateOptionsMenu()
            toast(getString(if (locked) R.string.toast_locked else R.string.toast_unlocked))
        }
    }

    private fun toggleMarkdown() {
        lifecycleScope.launch {
            val current = note ?: return@launch
            val markdown = !current.markdown
            note = current.copy(markdown = markdown)
            persist(force = true)
            buildToolBar()
            invalidateOptionsMenu()
            toast(
                getString(
                    if (markdown) R.string.toast_markdown_on else R.string.toast_markdown_off,
                ),
            )
        }
    }

    private fun preview() {
        lifecycleScope.launch {
            saveJob?.cancel()
            persist(force = true)
            val id = note?.id ?: 0L
            if (id == 0L) {
                toast(getString(R.string.preview_empty))
                return@launch
            }
            startActivity(
                Intent(this@EditActivity, MarkdownPreviewActivity::class.java)
                    .putExtra(MarkdownPreviewActivity.EXTRA_NOTE_ID, id),
            )
        }
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
                        persist()
                        val id = note?.id ?: 0L
                        if (id != 0L) repo.noteDao.setFolder(id, folderId)
                        note = note?.copy(folderId = folderId)
                    }
                }
                .show()
        }
    }

    private fun shareNote() {
        val title = binding.etTitle.text?.toString().orEmpty()
        val content = binding.etContent.text?.toString().orEmpty()
        val text = if (title.isBlank()) content else title + "\n\n" + content
        if (text.isBlank()) return
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.action_share)))
    }

    private fun confirmDelete() {
        MaterialAlertDialogBuilder(this)
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
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
        private const val MAX_HISTORY = 60
        private const val EXPORT_TXT = 0
        private const val EXPORT_MD = 1
        private const val EXPORT_PDF = 2

        private val NORMAL_TOOLS = listOf(
            ToolEntry("缩进", EditorTools.Tool.INDENT),
            ToolEntry("反缩进", EditorTools.Tool.OUTDENT),
            ToolEntry("• 列表", EditorTools.Tool.BULLET),
            ToolEntry("1. 列表", EditorTools.Tool.ORDERED),
            ToolEntry("☐ 待办", EditorTools.Tool.TODO),
            ToolEntry("引用", EditorTools.Tool.QUOTE),
            ToolEntry("段后空行", EditorTools.Tool.LINE_AFTER),
            ToolEntry("分隔线", EditorTools.Tool.DIVIDER),
            ToolEntry("时间", EditorTools.Tool.TIME),
            ToolEntry("日期", EditorTools.Tool.DATE),
            ToolEntry("，", EditorTools.Tool.COMMA),
            ToolEntry("。", EditorTools.Tool.PERIOD),
            ToolEntry("；", EditorTools.Tool.SEMICOLON),
            ToolEntry("“", EditorTools.Tool.QUOTE_LEFT),
            ToolEntry("”", EditorTools.Tool.QUOTE_RIGHT),
            ToolEntry("——", EditorTools.Tool.DASH),
            ToolEntry("插入图片", insertImage = true),
        )

        private val MARKDOWN_TOOLS = listOf(
            ToolEntry("H1", EditorTools.Tool.H1),
            ToolEntry("H2", EditorTools.Tool.H2),
            ToolEntry("H3", EditorTools.Tool.H3),
            ToolEntry("B", EditorTools.Tool.BOLD),
            ToolEntry("•", EditorTools.Tool.BULLET),
            ToolEntry("1.", EditorTools.Tool.ORDERED),
            ToolEntry("☐", EditorTools.Tool.TODO),
            ToolEntry("☑", EditorTools.Tool.TODO_DONE),
            ToolEntry("引用", EditorTools.Tool.QUOTE),
            ToolEntry("代码", EditorTools.Tool.CODE),
            ToolEntry("代码块", EditorTools.Tool.CODE_BLOCK),
            ToolEntry("链接", EditorTools.Tool.LINK),
            ToolEntry("分隔线", EditorTools.Tool.DIVIDER),
            ToolEntry("插入图片", insertImage = true),
        )
    }
}
