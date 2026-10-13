package com.jianji.note.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jianji.note.R
import com.jianji.note.data.Note
import com.jianji.note.databinding.ItemNoteBinding
import com.jianji.note.util.TimeFmt

class NoteListAdapter(
    private val onClick: (Note) -> Unit,
    private val onLongClick: (Note, View) -> Unit,
) : ListAdapter<Note, NoteListAdapter.NoteViewHolder>(DIFF) {

    private val bindingInflater: (ViewGroup) -> ItemNoteBinding = { parent ->
        ItemNoteBinding.inflate(LayoutInflater.from(parent.context), parent, false)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NoteViewHolder =
        NoteViewHolder(bindingInflater(parent))

    override fun onBindViewHolder(holder: NoteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class NoteViewHolder(
        private val binding: ItemNoteBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(note: Note) {
            binding.tvTitle.text = note.displayTitle

            val preview = note.preview
            binding.tvContent.text = preview
            binding.tvContent.visibility = if (preview.isBlank()) View.GONE else View.VISIBLE

            binding.tvTime.text = TimeFmt.friendly(note.updatedAt)
            binding.ivPin.visibility = if (note.pinned) View.VISIBLE else View.GONE

            binding.root.setOnClickListener { onClick(note) }
            binding.root.setOnLongClickListener { view ->
                onLongClick(note, view)
                true
            }
            binding.root.setCardBackgroundColor(
                binding.root.context.getColor(
                    if (note.pinned) R.color.pinned_surface else R.color.normal_surface,
                ),
            )
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Note>() {
            override fun areItemsTheSame(oldItem: Note, newItem: Note) = oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Note, newItem: Note) = oldItem == newItem
        }
    }
}
