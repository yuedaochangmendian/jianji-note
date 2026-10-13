package com.jianji.note

import com.jianji.note.data.Note
import com.jianji.note.util.Exporter
import com.jianji.note.util.ImageList
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExporterTest {

    private val note = Note(
        id = 1L,
        title = "会议记录",
        content = "第一条\n第二条",
        createdAt = 1_700_000_000_000L,
        updatedAt = 1_700_000_100_000L,
    )

    @Test
    fun `纯文本导出包含标题与正文`() {
        val text = Exporter.toPlainText(note)
        assertTrue(text.startsWith("会议记录"))
        assertTrue(text.contains("第一条\n第二条"))
        assertTrue(text.contains("创建于"))
    }

    @Test
    fun `Markdown 导出把标题提为一级标题`() {
        assertEquals("# 会议记录\n\n第一条\n第二条\n", Exporter.toMarkdown(note))
    }

    @Test
    fun `文件名过滤非法字符并补扩展名`() {
        val dirty = note.copy(title = "a/b:c*d?e\"f<g>h|i")
        assertEquals("a_b_c_d_e_f_g_h_i.md", Exporter.fileNameFor(dirty, "md"))
        // 标题为空时 displayTitle 会回退到「无标题」，而不是留空
        assertEquals("无标题.txt", Exporter.fileNameFor(note.copy(title = "  ", content = ""), "txt"))
    }

    @Test
    fun `图片列表编解码`() {
        assertEquals("", ImageList.encode(emptyList()))
        assertEquals("", ImageList.encode(listOf("  ", "")))

        val encoded = ImageList.encode(listOf("a.png", "b.jpg"))
        assertEquals(listOf("a.png", "b.jpg"), ImageList.decode(encoded))

        assertEquals(listOf("a.png", "b.jpg", "c.png"), ImageList.decode(ImageList.append(encoded, "c.png")))
        assertEquals(listOf("b.jpg"), ImageList.decode(ImageList.remove(encoded, "a.png")))

        assertEquals(emptyList<String>(), ImageList.decode(null))
        assertEquals(emptyList<String>(), ImageList.decode("不是 JSON"))
    }
}
