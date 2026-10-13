package com.jianji.note.ui

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.jianji.note.App
import com.jianji.note.R
import com.jianji.note.databinding.ActivityTrashBinding
import kotlinx.coroutines.launch

class TrashActivity : AppCompatActivity() {

    private lateinit var binding: ActivityTrashBinding

    private val repo get() = App.repo

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityTrashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)

        val adapter = NoteListAdapter(
            onClick = { note ->
                lifecycleScope.launch {
                    repo.noteDao.restore(note.id)
                    Snackbar.make(binding.root, R.string.toast_restored, Snackbar.LENGTH_SHORT).show()
                }
            },
            onLongClick = { note, _ ->
                MaterialAlertDialogBuilder(this)
                    .setTitle(R.string.trash_delete_title)
                    .setMessage(getString(R.string.trash_delete_message, note.displayTitle))
                    .setNegativeButton(android.R.string.cancel, null)
                    .setPositiveButton(R.string.action_delete) { _, _ ->
                        lifecycleScope.launch { repo.noteDao.deleteById(note.id) }
                    }
                    .show()
            },
        )
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                repo.noteDao.observeTrash().collect { notes ->
                    adapter.submitList(notes)
                    binding.emptyView.visibility = if (notes.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_trash, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            android.R.id.home -> finish()
            R.id.action_empty_trash -> MaterialAlertDialogBuilder(this)
                .setTitle(R.string.trash_empty_title)
                .setMessage(R.string.trash_empty_message)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.action_confirm) { _, _ ->
                    lifecycleScope.launch { repo.noteDao.emptyTrash() }
                }
                .show()
            else -> return super.onOptionsItemSelected(item)
        }
        return true
    }
}
