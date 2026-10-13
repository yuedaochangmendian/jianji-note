package com.jianji.note.util

import com.jianji.note.data.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 导出内容的文本生成。纯函数，方便单元测试；
 * 真正写文件的部分（PDF、图片）放在 ui 层。
 */
object Exporter {

    private val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun toPlainText(note: Note): String = buildString {
        append(note.displayTitle)
        append("\n")
        append("创建于 ").append(stamp.format(Date(note.createdAt)))
        append("　修改于 ").append(stamp.format(Date(note.updatedAt)))
        append("\n")
        append("─".repeat(24))
        append("\n\n")
        append(note.content)
    }

    /** Markdown 导出：标题统一提成一级标题，正文原样保留。 */
    fun toMarkdown(note: Note): String = buildString {
        val title = note.displayTitle
        append(if (title.startsWith("#")) title else "# $title")
        append("\n\n")
        if (note.content.isNotBlank()) {
            append(note.content)
            append("\n")
        }
    }

    /** 去掉文件名里不能用的字符，并限制长度。 */
    fun fileNameFor(note: Note, extension: String): String {
        val base = note.displayTitle
            .replace(Regex("""[\\/:*?"<>|\n\r\t]"""), "_")
            .trim()
            .take(40)
            .ifBlank { "note" }
        return "$base.$extension"
    }
}
