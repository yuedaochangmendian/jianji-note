package com.jianji.note

import com.jianji.note.data.Note
import com.jianji.note.util.TimeFmt
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteModelTest {

    @Test
    fun `没有标题时用正文第一行当标题`() {
        assertEquals("无标题", Note(title = "", content = "").displayTitle)
        assertEquals("第一行", Note(title = "", content = "第一行\n第二行").displayTitle)
        assertEquals("真实标题", Note(title = "真实标题", content = "第一行").displayTitle)
    }

    @Test
    fun `列表摘要跳过第一行`() {
        assertEquals("", Note(title = "t", content = "只有一行").preview)
        assertEquals("第二行", Note(title = "t", content = "第一行\n第二行").preview)
        assertEquals(
            "第二行\n第三行",
            Note(title = "t", content = "第一行\n\n第二行\n第三行").preview,
        )
    }

    @Test
    fun `时间戳为零时不显示时间`() {
        assertEquals("", TimeFmt.friendly(0L))
        assertEquals("", TimeFmt.friendly(-1L))
    }
}
