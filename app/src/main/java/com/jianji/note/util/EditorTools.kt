package com.jianji.note.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 编辑器工具栏的光标处操作。抽成纯函数（时间从参数传入）方便单元测试，
 * 界面层只负责把结果写回 EditText。
 */
object EditorTools {

    enum class Tool {
        H1, H2, H3,
        BOLD, BULLET, ORDERED, TODO, TODO_DONE, QUOTE,
        CODE, CODE_BLOCK, LINK, DIVIDER,
        INDENT, TIME, DATE,
    }

    data class Result(val text: String, val selection: Int)

    private val ORDERED_PREFIX = Regex("""^\d{1,3}[.)]\s+""")
    private const val INDENT_UNIT = "　　"

    fun apply(source: String, start: Int, end: Int, tool: Tool, now: Long): Result {
        val from = start.coerceIn(0, source.length)
        val to = end.coerceIn(from, source.length)
        return when (tool) {
            Tool.H1 -> prefixLines(source, from, to, "# ")
            Tool.H2 -> prefixLines(source, from, to, "## ")
            Tool.H3 -> prefixLines(source, from, to, "### ")
            Tool.BULLET -> prefixLines(source, from, to, "- ")
            Tool.TODO -> prefixLines(source, from, to, "- [ ] ")
            Tool.TODO_DONE -> prefixLines(source, from, to, "- [x] ")
            Tool.QUOTE -> prefixLines(source, from, to, "> ")
            Tool.INDENT -> prefixLines(source, from, to, INDENT_UNIT)
            Tool.ORDERED -> orderLines(source, from, to)
            Tool.BOLD -> wrap(source, from, to, "**", "**")
            Tool.CODE -> wrap(source, from, to, "`", "`")
            Tool.LINK -> wrap(source, from, to, "[", "](https://)")
            Tool.CODE_BLOCK -> insert(source, from, "```\n\n```", 4)
            Tool.DIVIDER -> insert(source, from, "\n---\n", 5)
            Tool.TIME -> {
                val text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(now))
                insert(source, from, text, text.length)
            }
            Tool.DATE -> {
                val text = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(now))
                insert(source, from, text, text.length)
            }
        }
    }

    /** 给选区覆盖的每一行加前缀；如果所有行都已有该前缀，则去掉（再次点击即取消）。 */
    private fun prefixLines(source: String, start: Int, end: Int, prefix: String): Result {
        val lineStart = lineStart(source, start)
        val lineEnd = lineEnd(source, end)
        val lines = source.substring(lineStart, lineEnd).split("\n")

        val allPrefixed = lines.all { it.startsWith(prefix) }
        val result = if (allPrefixed) {
            lines.map { it.removePrefix(prefix) }
        } else {
            lines.map { if (it.startsWith(prefix)) it else prefix + it }
        }.joinToString("\n")

        val text = source.substring(0, lineStart) + result + source.substring(lineEnd)
        return Result(text, lineStart + result.length)
    }

    /** 给每行编上 1. 2. 3.；已经全部带序号时改为去掉序号。 */
    private fun orderLines(source: String, start: Int, end: Int): Result {
        val lineStart = lineStart(source, start)
        val lineEnd = lineEnd(source, end)
        val lines = source.substring(lineStart, lineEnd).split("\n")

        val allNumbered = lines.all { ORDERED_PREFIX.containsMatchIn(it) }
        val result = if (allNumbered) {
            lines.map { it.replaceFirst(ORDERED_PREFIX, "") }
        } else {
            lines.mapIndexed { index, line ->
                "${index + 1}. " + line.replaceFirst(ORDERED_PREFIX, "")
            }
        }.joinToString("\n")

        val text = source.substring(0, lineStart) + result + source.substring(lineEnd)
        return Result(text, lineStart + result.length)
    }

    private fun wrap(source: String, start: Int, end: Int, open: String, close: String): Result {
        val selected = source.substring(start, end)
        val text = source.substring(0, start) + open + selected + close + source.substring(end)
        val cursor = if (selected.isEmpty()) start + open.length else start + open.length + selected.length + close.length
        return Result(text, cursor)
    }

    private fun insert(source: String, at: Int, snippet: String, cursorOffset: Int): Result {
        val text = source.substring(0, at) + snippet + source.substring(at)
        return Result(text, at + cursorOffset)
    }

    private fun lineStart(source: String, index: Int): Int {
        val found = source.lastIndexOf('\n', (index - 1).coerceAtLeast(0))
        return if (found < 0 || index == 0) 0 else found + 1
    }

    private fun lineEnd(source: String, index: Int): Int {
        val found = source.indexOf('\n', index)
        return if (found < 0) source.length else found
    }
}
