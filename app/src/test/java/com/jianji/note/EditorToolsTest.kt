package com.jianji.note

import com.jianji.note.util.EditorTools
import com.jianji.note.util.EditorTools.Tool
import org.junit.Assert.assertEquals
import org.junit.Test

class EditorToolsTest {

    @Test
    fun `加粗包裹选区并把光标放到末尾`() {
        val result = EditorTools.apply("你好世界", 2, 4, Tool.BOLD, 0L)
        assertEquals("你好**世界**", result.text)
        assertEquals(8, result.selection)
    }

    @Test
    fun `没有选区时加粗把光标放到中间`() {
        val result = EditorTools.apply("你好", 2, 2, Tool.BOLD, 0L)
        assertEquals("你好****", result.text)
        assertEquals(4, result.selection)
    }

    @Test
    fun `列表前缀可以开关`() {
        val added = EditorTools.apply("第一行\n第二行", 0, 7, Tool.BULLET, 0L)
        assertEquals("- 第一行\n- 第二行", added.text)

        val removed = EditorTools.apply(added.text, 0, added.text.length, Tool.BULLET, 0L)
        assertEquals("第一行\n第二行", removed.text)
    }

    @Test
    fun `有序列表自动编号并去掉旧编号`() {
        val result = EditorTools.apply("甲\n乙\n丙", 0, 5, Tool.ORDERED, 0L)
        assertEquals("1. 甲\n2. 乙\n3. 丙", result.text)
    }

    @Test
    fun `标题只加在光标所在行`() {
        val source = "第一行\n第二行"
        val result = EditorTools.apply(source, 4, 4, Tool.H1, 0L)
        assertEquals("第一行\n# 第二行", result.text)
    }

    @Test
    fun `缩进使用全角空格`() {
        val result = EditorTools.apply("正文", 0, 0, Tool.INDENT, 0L)
        assertEquals("　　正文", result.text)
    }

    @Test
    fun `插入时间与日期`() {
        val millis = 1727757240000L
        val time = EditorTools.apply("", 0, 0, Tool.TIME, millis)
        val date = EditorTools.apply("", 0, 0, Tool.DATE, millis)
        // 只断言格式长度与分隔符，避免依赖本机时区
        assertEquals(5, time.text.length)
        assertEquals(':', time.text[2])
        assertEquals(10, date.text.length)
        assertEquals(4, date.text.indexOf('-'))
    }

    @Test
    fun `代码块插入后光标停在中间`() {
        val result = EditorTools.apply("", 0, 0, Tool.CODE_BLOCK, 0L)
        assertEquals("```\n\n```", result.text)
        assertEquals(4, result.selection)
    }
}
