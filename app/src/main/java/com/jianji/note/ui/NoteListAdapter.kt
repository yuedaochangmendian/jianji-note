package com.jianji.note.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.jianji.note.R
import com.jianji.note.data.Note
import com.jianji.note.data.Prefs
import com.jianji.note.databinding.ItemNoteBriefBinding
import com.jianji.note.databinding.ItemNoteCardBinding
import com.jianji.note.databinding.ItemNoteDailyBinding
import com.jianji.note.databinding.ItemNoteGridBinding
import com.jianji.note.databinding.ItemNoteNormalBinding
import com.jianji.note.databinding.ItemNoteSummaryBinding
import com.jianji.note.util.TimeFmt

/**
 * 列表适配器，支持多种列表样式（对应原版的「列表样式」菜单）。
 * 每个样式一个 item 布局，公共的标题/摘要/时间/置顶/锁定绑定逻辑放在基类里。
 */
class NoteListAdapter(
    private val onClick: (Note) -> Unit,
    private val onLongClick: (Note, View) -> Unit,
) : ListAdapter<Note, NoteListAdapter.BaseHolder>(DIFF) {

    var layoutType: Int = Prefs.LAYOUT_NORMAL
        set(value) {
            if (field == value) return
            field = value
            @Suppress("NotifyDataSetChanged")
            notifyDataSetChanged()
        }

    override fun getItemViewType(position: Int): Int = layoutType

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            Prefs.LAYOUT_CARD ->
                CardHolder(ItemNoteCardBinding.inflate(inflater, parent, false))
            Prefs.LAYOUT_GRID ->
                GridHolder(ItemNoteGridBinding.inflate(inflater, parent, false))
            Prefs.LAYOUT_SUMMARY ->
                SummaryHolder(ItemNoteSummaryBinding.inflate(inflater, parent, false))
            Prefs.LAYOUT_DAILY ->
                DailyHolder(ItemNoteDailyBinding.inflate(inflater, parent, false))
            Prefs.LAYOUT_BRIEF ->
                BriefHolder(ItemNoteBriefBinding.inflate(inflater, parent, false))
            else ->
                NormalHolder(ItemNoteNormalBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: BaseHolder, position: Int) {
        holder.bind(getItem(position))
    }

    abstract inner class BaseHolder(view: View) : RecyclerView.ViewHolder(view) {

        abstract fun bind(note: Note)

        protected fun bindTexts(
            note: Note,
            title: TextView,
            content: TextView,
            time: TextView,
            pin: View?,
            lock: View?,
        ) {
            title.text = note.displayTitle
            val preview = note.preview
            content.text = preview
            content.visibility = if (preview.isBlank()) View.GONE else View.VISIBLE
            time.text = TimeFmt.friendly(note.updatedAt)
            pin?.visibility = if (note.pinned) View.VISIBLE else View.GONE
            lock?.visibility = if (note.locked) View.VISIBLE else View.GONE
            bindClicks(note)
        }

        protected fun bindClicks(note: Note) {
            itemView.setOnClickListener { onClick(note) }
            itemView.setOnLongClickListener { view ->
                onLongClick(note, view)
                true
            }
        }

        protected fun surfaceColor(note: Note): Int = itemView.context.getColor(
            if (note.pinned) R.color.pinned_surface else R.color.normal_surface,
        )
    }

    private inner class NormalHolder(
        private val binding: ItemNoteNormalBinding,
    ) : BaseHolder(binding.root) {
        override fun bind(note: Note) = bindTexts(
            note, binding.tvTitle, binding.tvContent, binding.tvTime, binding.ivPin, binding.ivLock,
        )
    }

    private inner class CardHolder(
        private val binding: ItemNoteCardBinding,
    ) : BaseHolder(binding.root) {
        override fun bind(note: Note) {
            binding.root.setCardBackgroundColor(surfaceColor(note))
            binding.accent.visibility = if (note.pinned) View.VISIBLE else View.GONE
            bindTexts(
                note, binding.tvTitle, binding.tvContent, binding.tvTime, binding.ivPin, binding.ivLock,
            )
        }
    }

    private inner class GridHolder(
        private val binding: ItemNoteGridBinding,
    ) : BaseHolder(binding.root) {
        override fun bind(note: Note) {
            binding.root.setCardBackgroundColor(surfaceColor(note))
            bindTexts(
                note, binding.tvTitle, binding.tvContent, binding.tvTime, binding.ivPin, binding.ivLock,
            )
        }
    }

    private inner class SummaryHolder(
        private val binding: ItemNoteSummaryBinding,
    ) : BaseHolder(binding.root) {
        override fun bind(note: Note) = bindTexts(
            note, binding.tvTitle, binding.tvContent, binding.tvTime, binding.ivPin, binding.ivLock,
        )
    }

    private inner class DailyHolder(
        private val binding: ItemNoteDailyBinding,
    ) : BaseHolder(binding.root) {
        override fun bind(note: Note) {
            binding.tvDay.text = TimeFmt.dayOfMonth(note.createdAt)
            binding.tvMonth.text = TimeFmt.monthLabel(note.createdAt)
            bindTexts(
                note, binding.tvTitle, binding.tvContent, binding.tvTime, binding.ivPin, binding.ivLock,
            )
        }
    }

    private inner class BriefHolder(
        private val binding: ItemNoteBriefBinding,
    ) : BaseHolder(binding.root) {
        override fun bind(note: Note) {
            binding.tvTitle.text = note.displayTitle
            binding.tvTime.text = TimeFmt.friendly(note.updatedAt)
            binding.ivPin.visibility = if (note.pinned) View.VISIBLE else View.GONE
            binding.ivLock.visibility = if (note.locked) View.VISIBLE else View.GONE
            binding.dot.visibility = if (note.pinned) View.VISIBLE else View.INVISIBLE
            bindClicks(note)
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Note>() {
            override fun areItemsTheSame(oldItem: Note, newItem: Note) = oldItem.id == newItem.id
            override fun areContentsTheSame(oldItem: Note, newItem: Note) = oldItem == newItem
        }
    }
}
