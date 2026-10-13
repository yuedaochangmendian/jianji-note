package com.jianji.note.util

/**
 * 极简 Markdown 解析器：把源码拆成「块」和行内片段。
 *
 * 刻意不依赖任何 Android 类型，这样能在 JVM 单元测试里直接验证；
 * 真正渲染成 Spannable 的活儿交给 ui 层的 MarkdownRender。
 */
object MarkdownParser {

    enum class Style { BOLD, ITALIC, STRIKE, CODE, LINK }

    /** 行内样式在 [Inline.text] 中的区间；LINK 会把地址放在 [extra]。 */
    data class InlineSpan(val start: Int, val end: Int, val style: Style, val extra: String? = null)

    data class Inline(val text: String, val spans: List<InlineSpan>) {
        companion object {
            val EMPTY = Inline("", emptyList())
        }
    }

    sealed interface Block {
        data class Heading(val level: Int, val inline: Inline) : Block
        data class Body(val inline: Inline) : Block

        /** index 为 null 表示无序列表，否则是序号。 */
        data class Bullet(val index: Int?, val inline: Inline) : Block
        data class Todo(val checked: Boolean, val inline: Inline) : Block
        data class Quote(val inline: Inline) : Block
        data class Code(val code: String) : Block
        data object Divider : Block
    }

    private val TODO_RE = Regex("""^[-*+]\s+\[([ xX])\]\s*(.*)$""")
    private val BULLET_RE = Regex("""^[-*+]\s+(.*)$""")
    private val ORDERED_RE = Regex("""^(\d{1,3})[.)]\s+(.*)$""")

    fun parse(source: String): List<Block> {
        val blocks = mutableListOf<Block>()
        val lines = source.replace("\r\n", "\n").split("\n")
        var inCode = false
        val code = StringBuilder()

        for (rawLine in lines) {
            val line = rawLine.trimEnd()
            val trimmed = line.trim()

            if (inCode) {
                if (trimmed.startsWith("```")) {
                    blocks += Block.Code(code.toString().trimEnd('\n'))
                    code.setLength(0)
                    inCode = false
                } else {
                    code.append(line).append('\n')
                }
                continue
            }

            if (trimmed.startsWith("```")) {
                inCode = true
                continue
            }

            when {
                trimmed.isEmpty() -> Unit
                isDivider(trimmed) -> blocks += Block.Divider
                headingLevel(trimmed) > 0 -> {
                    val level = headingLevel(trimmed)
                    blocks += Block.Heading(level, parseInline(trimmed.drop(level).trimStart()))
                }
                TODO_RE.matches(trimmed) -> {
                    val match = TODO_RE.find(trimmed)!!
                    blocks += Block.Todo(
                        checked = match.groupValues[1].equals("x", ignoreCase = true),
                        inline = parseInline(match.groupValues[2]),
                    )
                }
                ORDERED_RE.matches(trimmed) -> {
                    val match = ORDERED_RE.find(trimmed)!!
                    blocks += Block.Bullet(
                        index = match.groupValues[1].toIntOrNull(),
                        inline = parseInline(match.groupValues[2]),
                    )
                }
                BULLET_RE.matches(trimmed) -> {
                    val match = BULLET_RE.find(trimmed)!!
                    blocks += Block.Bullet(index = null, inline = parseInline(match.groupValues[1]))
                }
                trimmed.startsWith(">") ->
                    blocks += Block.Quote(parseInline(trimmed.removePrefix(">").trimStart()))
                else -> blocks += Block.Body(parseInline(trimmed))
            }
        }

        // 代码块没闭合也照样输出，避免内容凭空消失
        if (inCode && code.isNotEmpty()) blocks += Block.Code(code.toString().trimEnd('\n'))
        return blocks
    }

    fun parseInline(text: String): Inline {
        val out = StringBuilder()
        val spans = mutableListOf<InlineSpan>()
        var i = 0

        while (i < text.length) {
            val c = text[i]
            when {
                c == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > i + 1) {
                        val start = out.length
                        out.append(text, i + 1, end)
                        spans += InlineSpan(start, out.length, Style.CODE)
                        i = end + 1
                    } else {
                        out.append(c); i++
                    }
                }
                c == '[' -> {
                    val labelEnd = text.indexOf(']', i + 1)
                    val urlEnd = if (labelEnd > 0 && labelEnd + 1 < text.length && text[labelEnd + 1] == '(') {
                        text.indexOf(')', labelEnd + 2)
                    } else {
                        -1
                    }
                    if (labelEnd > i && urlEnd > labelEnd) {
                        val start = out.length
                        out.append(text, i + 1, labelEnd)
                        spans += InlineSpan(
                            start,
                            out.length,
                            Style.LINK,
                            text.substring(labelEnd + 2, urlEnd),
                        )
                        i = urlEnd + 1
                    } else {
                        out.append(c); i++
                    }
                }
                c == '*' && text.startsWith("**", i) -> {
                    i = appendPair(text, i, 2, out, spans, Style.BOLD)
                }
                c == '~' && text.startsWith("~~", i) -> {
                    i = appendPair(text, i, 2, out, spans, Style.STRIKE)
                }
                c == '*' || c == '_' -> {
                    i = appendPair(text, i, 1, out, spans, Style.ITALIC)
                }
                else -> {
                    out.append(c); i++
                }
            }
        }
        return Inline(out.toString(), spans)
    }

    /** 匹配成对标记；匹配不到就原样输出当前字符。返回新的扫描位置。 */
    private fun appendPair(
        text: String,
        index: Int,
        markerLength: Int,
        out: StringBuilder,
        spans: MutableList<InlineSpan>,
        style: Style,
    ): Int {
        val marker = text.substring(index, index + markerLength)
        val end = text.indexOf(marker, index + markerLength)
        if (end <= index + markerLength) {
            out.append(text, index, index + markerLength)
            return index + markerLength
        }
        val start = out.length
        out.append(text, index + markerLength, end)
        spans += InlineSpan(start, out.length, style)
        return end + markerLength
    }

    private fun headingLevel(line: String): Int {
        var count = 0
        while (count < line.length && line[count] == '#' && count < 6) count++
        return if (count in 1..6 && count < line.length && line[count] == ' ') count else 0
    }

    private fun isDivider(line: String): Boolean =
        line.length >= 3 && (line.all { it == '-' } || line.all { it == '*' } || line.all { it == '_' })
}
