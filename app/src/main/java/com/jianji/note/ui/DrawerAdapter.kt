package com.jianji.note.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jianji.note.R
import com.jianji.note.data.Folder
import com.jianji.note.databinding.ItemDrawerBinding

sealed interface DrawerItem {
    data object All : DrawerItem
    data object Uncategorized : DrawerItem
    data class FolderRow(val folder: Folder) : DrawerItem
    data object NewFolder : DrawerItem
}

class DrawerAdapter(
    private val onItemClick: (DrawerItem) -> Unit,
    private val onFolderLongClick: (Folder, View) -> Unit,
) : ListAdapter<DrawerItem, DrawerAdapter.DrawerViewHolder>(DIFF) {

    var selected: Selection = Selection.All
        set(value) {
            field = value
            notifyItemRangeChanged(0, itemCount)
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DrawerViewHolder =
        DrawerViewHolder(
            ItemDrawerBinding.inflate(LayoutInflater.from(parent.context), parent, false),
        )

    override fun onBindViewHolder(holder: DrawerViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DrawerViewHolder(
        private val binding: ItemDrawerBinding,
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: DrawerItem) {
            val context = binding.root.context
            val (label, icon) = when (item) {
                DrawerItem.All -> context.getString(R.string.folder_all) to R.drawable.ic_note
                DrawerItem.Uncategorized ->
                    context.getString(R.string.folder_uncategorized) to R.drawable.ic_note
                is DrawerItem.FolderRow -> item.folder.name to R.drawable.ic_folder
                DrawerItem.NewFolder ->
                    context.getString(R.string.folder_new) to R.drawable.ic_new_folder
            }

            binding.tvName.text = label
            binding.ivIcon.setImageResource(icon)

            val isSelected = when (item) {
                DrawerItem.All -> selected is Selection.All
                DrawerItem.Uncategorized -> selected is Selection.Uncategorized
                is DrawerItem.FolderRow ->
                    (selected as? Selection.InFolder)?.id == item.folder.id
                DrawerItem.NewFolder -> false
            }
            binding.root.isActivated = isSelected
            binding.tvName.isSelected = isSelected
            binding.root.setBackgroundColor(
                context.getColor(if (isSelected) R.color.drawer_selected else android.R.color.transparent),
            )

            binding.root.setOnClickListener { onItemClick(item) }
            binding.root.setOnLongClickListener { view ->
                if (item is DrawerItem.FolderRow) {
                    onFolderLongClick(item.folder, view)
                    true
                } else {
                    false
                }
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<DrawerItem>() {
            override fun areItemsTheSame(oldItem: DrawerItem, newItem: DrawerItem): Boolean =
                when {
                    oldItem is DrawerItem.FolderRow && newItem is DrawerItem.FolderRow ->
                        oldItem.folder.id == newItem.folder.id
                    else -> oldItem::class == newItem::class
                }

            override fun areContentsTheSame(oldItem: DrawerItem, newItem: DrawerItem): Boolean =
                oldItem == newItem
        }
    }
}
