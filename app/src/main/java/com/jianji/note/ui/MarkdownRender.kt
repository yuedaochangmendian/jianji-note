package com.jianji.note.ui

import android.content.Context
import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.StyleSpan
import android.text.style.TypefaceSpan
import android.text.style.URLSpan
import androidx.core.content.ContextCompat
import com.jianji.note.R
import com.jianji.note.util.MarkdownParser

/**
 * 把 [MarkdownParser] 解析出的块渲染成可显示的 Spannable。
 * 用 TextView 而不是 WebView：主题色能跟着深色模式走，也不用往 assets 里塞 JS。
 */
object MarkdownRender {

    fun render(context: Context, source: String): CharSequence {
        val blocks = MarkdownParser.parse(source)
        val codeBg = ContextCompat.getColor(context, R.color.md_code_bg)
        val codeFg = ContextCompat.getColor(context, R.color.md_code_fg)
        val quoteColor = ContextCompat.getColor(context, R.color.md_quote)
        val linkColor = ContextCompat.getColor(context, R.color.md_link)
        val indent = (16 * context.resources.displayMetrics.density).toInt()

        val sb = SpannableStringBuilder()
        blocks.forEachIndexed { index, block ->
            if (index > 0) sb.append("\n")
            when (block) {
                is MarkdownParser.Block.Heading -> {
                    val start = sb.length
                    appendInline(sb, block.inline, codeBg, codeFg, linkColor)
                    val scale = when (block.level) {
                        1 -> 1.6f
                        2 -> 1.4f
                        else -> 1.2f
                    }
                    sb.setSpan(
                        RelativeSizeSpan(scale), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    sb.setSpan(
                        StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }

                is MarkdownParser.Block.Body ->
                    appendInline(sb, block.inline, codeBg, codeFg, linkColor)

                is MarkdownParser.Block.Bullet -> {
                    val marker = block.index?.let { "$it. " } ?: "• "
                    sb.append(marker)
                    val start = sb.length
                    appendInline(sb, block.inline, codeBg, codeFg, linkColor)
                    sb.setSpan(
                        LeadingMarginSpan.Standard(indent), start, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }

                is MarkdownParser.Block.Todo -> {
                    sb.append(if (block.checked) "☑ " else "☐ ")
                    appendInline(sb, block.inline, codeBg, codeFg, linkColor)
                }

                is MarkdownParser.Block.Quote -> {
                    sb.append("│ ")
                    val start = sb.length
                    appendInline(sb, block.inline, codeBg, codeFg, linkColor)
                    sb.setSpan(
                        ForegroundColorSpan(quoteColor), start, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    sb.setSpan(
                        LeadingMarginSpan.Standard(indent), start, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }

                is MarkdownParser.Block.Code -> {
                    val start = sb.length
                    block.code.lines().forEachIndexed { lineIndex, line ->
                        if (lineIndex > 0) sb.append("\n")
                        sb.append(line)
                    }
                    sb.setSpan(
                        TypefaceSpan("monospace"), start, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    sb.setSpan(
                        BackgroundColorSpan(codeBg), start, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    sb.setSpan(
                        ForegroundColorSpan(codeFg), start, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }

                MarkdownParser.Block.Divider -> {
                    val start = sb.length
                    sb.append("────────────────")
                    sb.setSpan(
                        ForegroundColorSpan(quoteColor), start, sb.length,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }
            }
        }

        if (sb.isEmpty()) sb.append(context.getString(R.string.preview_empty))
        return sb
    }

    private fun appendInline(
        sb: SpannableStringBuilder,
        inline: MarkdownParser.Inline,
        codeBg: Int,
        codeFg: Int,
        linkColor: Int,
    ) {
        val base = sb.length
        sb.append(inline.text)
        inline.spans.forEach { span ->
            val start = base + span.start
            val end = base + span.end
            if (start >= end) return@forEach
            when (span.style) {
                MarkdownParser.Style.BOLD -> sb.setSpan(
                    StyleSpan(Typeface.BOLD), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )

                MarkdownParser.Style.ITALIC -> sb.setSpan(
                    StyleSpan(Typeface.ITALIC), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )

                MarkdownParser.Style.STRIKE -> sb.setSpan(
                    StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )

                MarkdownParser.Style.CODE -> {
                    sb.setSpan(
                        TypefaceSpan("monospace"), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    sb.setSpan(
                        BackgroundColorSpan(codeBg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    sb.setSpan(
                        ForegroundColorSpan(codeFg), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                }

                MarkdownParser.Style.LINK -> {
                    sb.setSpan(
                        ForegroundColorSpan(linkColor), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                    )
                    span.extra?.let { url ->
                        sb.setSpan(URLSpan(url), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                }
            }
        }
    }
}
