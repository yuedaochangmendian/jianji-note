package com.jianji.note

import com.jianji.note.util.MarkdownParser
import com.jianji.note.util.MarkdownParser.Block
import com.jianji.note.util.MarkdownParser.Style
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownParserTest {

    @Test
    fun `标题按层级解析`() {
        val blocks = MarkdownParser.parse("# 一级\n## 二级\n###三级没有空格")
        assertEquals(1, (blocks[0] as Block.Heading).level)
        assertEquals("一级", (blocks[0] as Block.Heading).inline.text)
        assertEquals(2, (blocks[1] as Block.Heading).level)
        // 井号后面没有空格不算标题
        assertTrue(blocks[2] is Block.Body)
    }

    @Test
    fun `行内样式的位置能对上`() {
        val inline = MarkdownParser.parseInline("前**粗**后")
        assertEquals("前粗后", inline.text)
        assertEquals(1, inline.spans.size)
        val span = inline.spans[0]
        assertEquals(Style.BOLD, span.style)
        assertEquals("粗", inline.text.substring(span.start, span.end))
    }

    @Test
    fun `行内代码与链接`() {
        val inline = MarkdownParser.parseInline("用 `code` 见 [文档](https://example.com) 吧")
        assertEquals("用 code 见 文档 吧", inline.text)

        val code = inline.spans.first { it.style == Style.CODE }
        assertEquals("code", inline.text.substring(code.start, code.end))

        val link = inline.spans.first { it.style == Style.LINK }
        assertEquals("文档", inline.text.substring(link.start, link.end))
        assertEquals("https://example.com", link.extra)
    }

    @Test
    fun `未闭合的标记原样保留`() {
        assertEquals("未完**成的加粗", MarkdownParser.parseInline("未完**成的加粗").text)
        assertEquals("单个*号", MarkdownParser.parseInline("单个*号").text)
    }

    @Test
    fun `列表与待办`() {
        val blocks = MarkdownParser.parse(
            """
            - 无序
            1. 有序
            - [ ] 没做
            - [x] 做完了
            > 引用
            """.trimIndent(),
        )
        assertEquals(null, (blocks[0] as Block.Bullet).index)
        assertEquals("无序", (blocks[0] as Block.Bullet).inline.text)
        assertEquals(1, (blocks[1] as Block.Bullet).index)
        assertEquals(false, (blocks[2] as Block.Todo).checked)
        assertEquals(true, (blocks[3] as Block.Todo).checked)
        assertEquals("引用", (blocks[4] as Block.Quote).inline.text)
    }

    @Test
    fun `代码块内的内容不做行内解析`() {
        val blocks = MarkdownParser.parse("```\n**不是加粗**\n```")
        assertEquals(1, blocks.size)
        assertEquals("**不是加粗**", (blocks[0] as Block.Code).code)
    }

    @Test
    fun `分割线`() {
        val blocks = MarkdownParser.parse("上面\n---\n下面")
        assertEquals(3, blocks.size)
        assertTrue(blocks[1] is Block.Divider)
    }
}
