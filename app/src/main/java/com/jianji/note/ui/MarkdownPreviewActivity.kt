package com.jianji.note.ui

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.view.MenuItem
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import androidx.lifecycle.lifecycleScope
import com.jianji.note.App
import com.jianji.note.R
import com.jianji.note.data.Prefs
import com.jianji.note.databinding.ActivityMarkdownPreviewBinding
import com.jianji.note.util.applySystemBarInsets
import com.jianji.note.util.AppFiles
import com.jianji.note.util.ImageList
import com.jianji.note.util.ImageLoader
import kotlinx.coroutines.launch

/** 预览：Markdown 笔记渲染后显示，普通笔记按纯文本显示。 */
class MarkdownPreviewActivity : BaseActivity() {

    private lateinit var binding: ActivityMarkdownPreviewBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMarkdownPreviewBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.root.applySystemBarInsets()
        applyAppBarTint(binding.appBar)

        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.setHomeAsUpIndicator(R.drawable.ic_arrow_back)

        val noteId = intent.getLongExtra(EXTRA_NOTE_ID, 0L)
        lifecycleScope.launch {
            val note = App.repo.noteDao.findById(noteId)
            val title = note?.displayTitle.orEmpty()
            supportActionBar?.title = title.ifBlank { getString(R.string.title_preview) }

            binding.tvPreview.textSize = Prefs.fontSize.toFloat()
            // [img] 占位符由下方的图片区呈现，正文里去掉
            val source = note?.content.orEmpty().replace("[img]", "").trim()
            binding.tvPreview.text = if (note?.markdown == true) {
                MarkdownRender.render(this@MarkdownPreviewActivity, source, Prefs.fontSize.toFloat())
            } else {
                source.ifBlank { getString(R.string.preview_empty) }
            }
            binding.tvPreview.movementMethod = LinkMovementMethod.getInstance()
            renderImages(ImageList.decode(note?.images))
        }
    }

    private fun renderImages(names: List<String>) {
        binding.imageColumn.removeAllViews()
        val maxWidth = resources.displayMetrics.widthPixels.coerceAtMost(1080)
        names.forEach { name ->
            val file = AppFiles.file(this, AppFiles.KIND_IMAGE, name)
            if (!file.exists()) return@forEach
            val imageView = ImageView(this).apply {
                adjustViewBounds = true
                scaleType = ImageView.ScaleType.FIT_CENTER
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                ).apply { bottomMargin = (12 * resources.displayMetrics.density).toInt() }
            }
            ImageLoader.loadThumbnail(imageView, file, maxWidth)
            binding.imageColumn.addView(imageView)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    companion object {
        const val EXTRA_NOTE_ID = "note_id"
    }
}
