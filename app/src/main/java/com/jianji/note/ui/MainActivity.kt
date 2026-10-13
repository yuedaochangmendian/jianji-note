package com.jianji.note.ui

import android.content.Intent
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.EditText
import android.widget.PopupMenu
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SearchView
import androidx.core.view.GravityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jianji.note.App
import com.jianji.note.R
import com.jianji.note.data.Folder
import com.jianji.note.data.Note
import com.jianji.note.data.Prefs
import com.jianji.note.databinding.ActivityMainBinding
import com.jianji.note.util.applySystemBarInsets
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class MainActivity : BaseActivity() {

    private lateinit var binding: ActivityMainBinding

    private val repo get() = App.repo

    private val selection = MutableStateFlow<Selection>(Selection.All)
    private val searchQuery = MutableStateFlow("")

    private lateinit var noteAdapter: NoteListAdapter
    private lateinit var drawerAdapter: DrawerAdapter

    private var lockPending = false

    private val lockLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        lockPending = false
        if (result.resultCode != RESULT_OK) finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 让出状态栏 / 导航栏高度，否则内容会和系统栏重叠
        binding.coordinator.applySystemBarInsets()
        binding.drawerPanel.applySystemBarInsets(horizontal = false)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_menu)

        noteAdapter = NoteListAdapter(
            onClick = { note -> openEditor(note.id) },
            onLongClick = { note, view -> showNoteMenu(note, view) },
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = noteAdapter

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

        binding.fabAdd.setOnClickListener { openEditor(null) }

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
    }

    override fun onStart() {
        super.onStart()
        requestUnlockIfNeeded()
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
                selection.collect { sel ->
                    drawerAdapter.selected = sel
                    supportActionBar?.title = when (sel) {
                        Selection.All -> getString(R.string.folder_all)
                        Selection.Uncategorized -> getString(R.string.folder_uncategorized)
                        is Selection.InFolder -> repo.folderDao.loadAll()
                            .firstOrNull { it.id == sel.id }?.name
                            ?: getString(R.string.app_name)
                    }
                }
            }
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(selection, searchQuery) { sel, query -> sel to query }
                    .flatMapLatest { (sel, query) ->
                        if (query.isBlank()) {
                            when (sel) {
                                Selection.All -> repo.noteDao.observeActive()
                                Selection.Uncategorized ->
                                    repo.noteDao.observeActiveInFolder(null)
                                is Selection.InFolder ->
                                    repo.noteDao.observeActiveInFolder(sel.id)
                            }
                        } else {
                            repo.noteDao.search(query)
                        }
                    }
                    .collect { notes ->
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

    private fun openEditor(noteId: Long?) {
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
                    R.id.action_delete -> lifecycleScope.launch {
                        repo.noteDao.moveToTrash(note.id, System.currentTimeMillis())
                        Snackbar.make(
                            binding.coordinator,
                            R.string.toast_moved_to_trash,
                            Snackbar.LENGTH_LONG,
                        )
                            .setAction(R.string.action_undo) {
                                lifecycleScope.launch { repo.noteDao.restore(note.id) }
                            }
                            .show()
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

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> binding.drawerLayout.openDrawer(GravityCompat.START)
            R.id.action_trash -> startActivity(Intent(this, TrashActivity::class.java))
            R.id.action_settings -> startActivity(Intent(this, SettingsActivity::class.java))
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }

}
