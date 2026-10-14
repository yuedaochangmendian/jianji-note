package com.jianji.note.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jianji.note.App
import com.jianji.note.R
import com.jianji.note.data.Folder
import com.jianji.note.data.Note
import com.jianji.note.data.NoteSort
import com.jianji.note.data.Prefs
import com.jianji.note.databinding.ActivityMainBinding
import com.jianji.note.util.applySystemBarInsets
import com.jianji.note.util.Exporter
import com.jianji.note.widget.ACTION_NEW_NOTE
import com.jianji.note.widget.ACTION_SEARCH
import com.jianji.note.widget.NoteWidgetProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    private val repo get() = App.repo

    private val selection = MutableStateFlow<Selection>(Selection.All)
    private val searchQuery = MutableStateFlow("")
    private val sortOrder = MutableStateFlow(Prefs.sortBy)

    private lateinit var noteAdapter: NoteListAdapter
    private lateinit var drawerAdapter: DrawerAdapter

    /** 整屏解锁（进入应用时）与单条笔记解锁两个流程分开，避免互相覆盖状态。 */
    private var lockPending = false
    private var pendingUnlockNoteId: Long? = null
    private var pendingSearch = false

    private val lockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        lockPending = false
        if (result.resultCode != RESULT_OK) finish()
    }

    private val noteUnlockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val noteId = pendingUnlockNoteId
        pendingUnlockNoteId = null
        if (result.resultCode == RESULT_OK && noteId != null) {
            startEditor(noteId)
        }
    }

    private val exportAllTxt = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri -> uri?.let { exportAll(it, markdown = false) } }

    private val exportAllMd = registerForActivityResult(
        ActivityResultContracts.CreateDocument("text/markdown"),
    ) { uri -> uri?.let { exportAll(it, markdown = true) } }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 让出状态栏 / 导航栏高度，否则内容会和系统栏重叠
        binding.coordinator.applySystemBarInsets()
        binding.drawerPanel.applySystemBarInsets()
        applyAppBarTint(binding.appBar)
        UiStyle.applyBackground(binding.coordinator, Prefs.backgroundMain)
        UiStyle.applyBackground(binding.drawerPanel, Prefs.backgroundDrawer)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)

        noteAdapter = NoteListAdapter(
            typeface = UiStyle.typeface(this),
            onClick = { note -> requestOpen(note.id) },
            onLongClick = { note, view -> showNoteMenu(note, view) },
        )
        binding.recyclerView.adapter = noteAdapter
        applyListLayout()

        drawerAdapter = DrawerAdapter(
            onItemClick = { item ->
                when (item) {
                    DrawerItem.All -> selection.value = Selection.All
                    DrawerItem.Uncategorized -> selection.value = Selection.Uncategorized
                    is DrawerItem.FolderRow -> selection.value = Selection.InFolder(item.folder.id)
                    DrawerItem.NewFolder -> {
                        createFolder()
                        return@DrawerAdapter
                    }
                }
                binding.drawerLayout.closeDrawer(GravityCompat.START)
            },
            onFolderLongClick = { folder, view -> showFolderMenu(folder, view) },
        )
        binding.drawerList.layoutManager = LinearLayoutManager(this)
        binding.drawerList.adapter = drawerAdapter

        binding.fabAdd.setOnClickListener { startEditor(null) }

        onBackPressedDispatcher.addCallback(
            this,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                        binding.drawerLayout.closeDrawer(GravityCompat.START)
                    } else {
                        isEnabled = false
                        onBackPressedDispatcher.onBackPressed()
                    }
                }
            },
        )

        observeData()

        // 桌面快捷方式 / 小组件带过来的动作
        handleAction(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAction(intent)
    }

    private fun handleAction(intent: Intent?) {
        when (intent?.action) {
            ACTION_NEW_NOTE -> startEditor(null)
            ACTION_SEARCH -> pendingSearch = true
        }
    }

    override fun onStart() {
        super.onStart()
        requestUnlockIfNeeded()
        // 笔记改动后回到列表时同步刷新桌面小组件
        NoteWidgetProvider.refreshAll(this)
    }

    override fun onStop() {
        super.onStop()
        // 离开应用时重新上锁，下次进入需要再次输入密码
        if (Prefs.passcodeEnabled) Prefs.unlocked = false
    }

    private fun requestUnlockIfNeeded() {
        if (!Prefs.passcodeEnabled || Prefs.unlocked || lockPending) return
        lockPending = true
        lockLauncher.launch(
            Intent(this, PasscodeActivity::class.java)
                .putExtra(PasscodeActivity.EXTRA_MODE, PasscodeActivity.MODE_CHECK),
        )
    }

    private fun applyListLayout() {
        noteAdapter.layoutType = Prefs.layoutType
        binding.recyclerView.layoutManager = if (Prefs.layoutType == Prefs.LAYOUT_GRID) {
            GridLayoutManager(this, 2)
        } else {
            LinearLayoutManager(this)
        }
    }

    private fun observeData() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.folderDao.observeAll().collect { folders ->
                    val items = buildList {
                        add(DrawerItem.All)
                        add(DrawerItem.Uncategorized)
                        folders.forEach { add(DrawerItem.FolderRow(it)) }
                        add(DrawerItem.NewFolder)
                    }
                    drawerAdapter.submitList(items)
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                selection.collect { current ->
                    drawerAdapter.selected = current
                    supportActionBar?.title = when (current) {
                        Selection.All -> getString(R.string.folder_all)
                        Selection.Uncategorized -> getString(R.string.folder_uncategorized)
                        is Selection.InFolder -> repo.folderDao.loadAll()
                            .firstOrNull { it.id == current.id }?.name
                            ?: getString(R.string.app_name)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(selection, searchQuery, sortOrder) { sel, query, sort ->
                    Triple(sel, query, sort)
                }.flatMapLatest { (sel, query, sort) ->
                    when {
                        query.isNotBlank() -> repo.noteDao.search(query)
                        sel is Selection.InFolder -> repo.noteDao.observeActiveInFolder(sel.id)
                        sel is Selection.Uncategorized -> repo.noteDao.observeActiveInFolder(null)
                        else -> repo.noteDao.observeActive()
                    }.map { NoteSort.sort(it, sort) }
                }.collect { notes ->
                    noteAdapter.submitList(notes)
                    val empty = notes.isEmpty()
                    binding.emptyView.visibility = if (empty) View.VISIBLE else View.GONE
                    binding.emptyView.text = getString(
                        if (searchQuery.value.isBlank()) R.string.empty_hint
                        else R.string.empty_search,
                    )
                }
            }
        }
    }

    /** 锁定的笔记要先过密码才打开。 */
    private fun requestOpen(noteId: Long) {
        lifecycleScope.launch {
            val note = repo.noteDao.findById(noteId)
            if (note?.locked == true && Prefs.passcodeEnabled) {
                pendingUnlockNoteId = noteId
                noteUnlockLauncher.launch(
                    Intent(this@MainActivity, PasscodeActivity::class.java)
                        .putExtra(PasscodeActivity.EXTRA_MODE, PasscodeActivity.MODE_CHECK),
                )
            } else {
                startEditor(noteId)
            }
        }
    }

    private fun startEditor(noteId: Long?) {
        startActivity(
            Intent(this, EditActivity::class.java)
                .putExtra(EditActivity.EXTRA_NOTE_ID, noteId ?: 0L),
        )
    }

    private fun showNoteMenu(note: Note, anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.menu_note, menu)
            menu.findItem(R.id.action_pin).title =
                getString(if (note.pinned) R.string.action_unpin else R.string.action_pin)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_pin -> lifecycleScope.launch {
                        val pinned = !note.pinned
                        repo.noteDao.setPinned(note.id, pinned)
                        Snackbar.make(
                            binding.coordinator,
                            if (pinned) R.string.toast_pinned else R.string.toast_unpinned,
                            Snackbar.LENGTH_SHORT,
                        ).show()
                    }
                    R.id.action_move -> pickFolder { folderId ->
                        lifecycleScope.launch { repo.noteDao.setFolder(note.id, folderId) }
                    }
                    R.id.action_delete -> {
                        if (note.locked) {
                            toast(getString(R.string.toast_note_locked_cant_delete))
                        } else {
                            lifecycleScope.launch {
                                repo.noteDao.moveToTrash(note.id, System.currentTimeMillis())
                                Snackbar.make(
                                    binding.coordinator,
                                    R.string.toast_moved_to_trash,
                                    Snackbar.LENGTH_LONG,
                                ).setAction(R.string.action_undo) {
                                    lifecycleScope.launch { repo.noteDao.restore(note.id) }
                                }.show()
                            }
                        }
                    }
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    private fun showFolderMenu(folder: Folder, anchor: View) {
        PopupMenu(this, anchor).apply {
            menuInflater.inflate(R.menu.menu_folder, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.action_rename -> renameFolder(folder)
                    R.id.action_delete_folder -> MaterialAlertDialogBuilder(this@MainActivity)
                        .setTitle(R.string.folder_delete_title)
                        .setMessage(getString(R.string.folder_delete_message, folder.name))
                        .setNegativeButton(android.R.string.cancel, null)
                        .setPositiveButton(R.string.action_delete) { _, _ ->
                            lifecycleScope.launch {
                                repo.deleteFolder(folder)
                                if ((selection.value as? Selection.InFolder)?.id == folder.id) {
                                    selection.value = Selection.All
                                }
                            }
                        }
                        .show()
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
            show()
        }
    }

    private fun createFolder() {
        showNameDialog(null) { name -> lifecycleScope.launch { repo.createFolder(name) } }
    }

    private fun renameFolder(folder: Folder) {
        showNameDialog(folder.name) { name ->
            lifecycleScope.launch { repo.renameFolder(folder, name) }
        }
    }

    private fun showNameDialog(initial: String?, onConfirm: (String) -> Unit) {
        val input = EditText(this).apply {
            setText(initial.orEmpty())
            hint = getString(R.string.folder_name_hint)
            setPadding(48, 32, 48, 32)
        }
        MaterialAlertDialogBuilder(this)
            .setTitle(if (initial == null) R.string.folder_new else R.string.folder_rename)
            .setView(input)
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) onConfirm(name)
            }
            .show()
    }

    private fun pickFolder(onPicked: (Long?) -> Unit) {
        lifecycleScope.launch {
            val folders = repo.folderDao.loadAll()
            val labels = buildList {
                add(getString(R.string.folder_uncategorized))
                folders.forEach { add(it.name) }
            }
            MaterialAlertDialogBuilder(this@MainActivity)
                .setTitle(R.string.action_move)
                .setItems(labels.toTypedArray()) { _, index ->
                    onPicked(if (index == 0) null else folders[index - 1].id)
                }
                .show()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        val searchItem = menu.findItem(R.id.action_search)
        (searchItem.actionView as? SearchView)?.apply {
            queryHint = getString(R.string.action_search)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean {
                    searchQuery.value = query.orEmpty()
                    return true
                }

                override fun onQueryTextChange(newText: String?): Boolean {
                    searchQuery.value = newText.orEmpty()
                    return true
                }
            })
        }
        return true
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        checkMenuItem(menu, sortItemId())
        checkMenuItem(menu, layoutItemId())
        if (pendingSearch) {
            pendingSearch = false
            menu.findItem(R.id.action_search)?.expandActionView()
        }
        return super.onPrepareOptionsMenu(menu)
    }

    private fun sortItemId(): Int = when (Prefs.sortBy) {
        NoteSort.CREATED_DESC -> R.id.sort_created_desc
        NoteSort.CREATED_ASC -> R.id.sort_created_asc
        else -> R.id.sort_updated
    }

    private fun layoutItemId(): Int = when (Prefs.layoutType) {
        Prefs.LAYOUT_CARD -> R.id.layout_card
        Prefs.LAYOUT_GRID -> R.id.layout_grid
        Prefs.LAYOUT_SUMMARY -> R.id.layout_summary
        Prefs.LAYOUT_DAILY -> R.id.layout_daily
        Prefs.LAYOUT_BRIEF -> R.id.layout_brief
        Prefs.LAYOUT_MD -> R.id.layout_md
        Prefs.LAYOUT_MI_NOTE -> R.id.layout_mi_note
        Prefs.LAYOUT_SMARTISAN -> R.id.layout_smartisan
        Prefs.LAYOUT_BEAR -> R.id.layout_bear
        else -> R.id.layout_normal
    }

    /** 子菜单里的条目不能靠 Menu.findItem 找到，得递归下去。 */
    private fun checkMenuItem(menu: Menu, id: Int) {
        for (index in 0 until menu.size()) {
            val item = menu.getItem(index)
            if (item.itemId == id) {
                item.isChecked = true
                return
            }
            item.subMenu?.let { checkMenuItem(it, id) }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> binding.drawerLayout.openDrawer(GravityCompat.START)
            R.id.action_trash -> startActivity(Intent(this, TrashActivity::class.java))
            R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))

            R.id.sort_updated -> setSort(NoteSort.UPDATED_DESC)
            R.id.sort_created_desc -> setSort(NoteSort.CREATED_DESC)
            R.id.sort_created_asc -> setSort(NoteSort.CREATED_ASC)

            R.id.layout_normal -> setLayout(Prefs.LAYOUT_NORMAL)
            R.id.layout_card -> setLayout(Prefs.LAYOUT_CARD)
            R.id.layout_grid -> setLayout(Prefs.LAYOUT_GRID)
            R.id.layout_summary -> setLayout(Prefs.LAYOUT_SUMMARY)
            R.id.layout_daily -> setLayout(Prefs.LAYOUT_DAILY)
            R.id.layout_brief -> setLayout(Prefs.LAYOUT_BRIEF)
            R.id.layout_md -> setLayout(Prefs.LAYOUT_MD)
            R.id.layout_mi_note -> setLayout(Prefs.LAYOUT_MI_NOTE)
            R.id.layout_smartisan -> setLayout(Prefs.LAYOUT_SMARTISAN)
            R.id.layout_bear -> setLayout(Prefs.LAYOUT_BEAR)
            R.id.export_all_txt -> exportAllTxt.launch("jianji-notes.txt")
            R.id.export_all_md -> exportAllMd.launch("jianji-notes.md")

            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

    private fun setSort(value: Int) {
        Prefs.sortBy = value
        sortOrder.value = value
        invalidateOptionsMenu()
    }

    private fun setLayout(value: Int) {
        Prefs.layoutType = value
        applyListLayout()
        invalidateOptionsMenu()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    /** 把当前全部未删除的笔记导出成一个文件。 */
    private fun exportAll(uri: Uri, markdown: Boolean) {
        lifecycleScope.launch {
            val notes = NoteSort.sort(repo.noteDao.loadAll().filter { it.deletedAt == null }, Prefs.sortBy)
            val text = notes.joinToString("\n\n---\n\n") {
                if (markdown) Exporter.toMarkdown(it) else Exporter.toPlainText(it)
            }
            val ok = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray()) }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            toast(
                if (ok) {
                    getString(R.string.export_success_named, uri.lastPathSegment.orEmpty())
                } else {
                    getString(R.string.export_pdf_failed)
                },
            )
        }
    }
}
