package com.jianji.note

import com.jianji.note.data.Folder
import com.jianji.note.data.Note
import com.jianji.note.util.Backup
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupTest {

    @Test
    fun `导出再导入后笔记与文件夹内容保持一致`() {
        val folders = listOf(Folder(id = 7L, name = "工作", position = 0, createdAt = 1000L))
        val notes = listOf(
            Note(
                id = 1L, title = "标题", content = "正文内容", folderId = 7L,
                pinned = true, createdAt = 10L, updatedAt = 20L,
            ),
            Note(
                id = 2L, title = "", content = "没有标题的笔记", folderId = null,
                createdAt = 30L, updatedAt = 40L,
            ),
            Note(
                id = 3L, title = "已删除", content = "x", deletedAt = 99L,
                createdAt = 50L, updatedAt = 60L,
            ),
        )

        val parsed = Backup.decode(Backup.encode(notes, folders))

        assertEquals(1, parsed.folders.size)
        val (oldFolderId, folder) = parsed.folders[0]
        assertEquals(7L, oldFolderId)
        assertEquals("工作", folder.name)
        // 导入时会重新分配主键，因此内容里的 id 必须归零
        assertEquals(0L, folder.id)

        assertEquals(3, parsed.notes.size)

        val (firstFolderId, first) = parsed.notes[0]
        assertEquals(7L, firstFolderId)
        assertEquals("标题", first.title)
        assertEquals("正文内容", first.content)
        assertTrue(first.pinned)
        assertEquals(10L, first.createdAt)
        assertEquals(20L, first.updatedAt)

        assertNull(parsed.notes[1].first)
        assertEquals("没有标题的笔记", parsed.notes[1].second.content)
        assertFalse(parsed.notes[1].second.pinned)

        assertEquals(99L, parsed.notes[2].second.deletedAt)
    }

    @Test
    fun `缺少可选字段时也能解析`() {
        val parsed = Backup.decode("""{"notes":[{"title":"t","content":"c"}]}""")

        assertEquals(0, parsed.folders.size)
        assertEquals(1, parsed.notes.size)
        assertNull(parsed.notes[0].first)
        assertFalse(parsed.notes[0].second.pinned)
        assertNull(parsed.notes[0].second.deletedAt)
    }

    @Test
    fun `导出的 JSON 带有格式版本`() {
        val json = Backup.encode(emptyList(), emptyList())
        assertTrue(json.contains("\"app\": \"jianji\""))
        assertTrue(json.contains("\"version\": 1"))
    }
}
