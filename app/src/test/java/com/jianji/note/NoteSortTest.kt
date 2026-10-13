package com.jianji.note

import com.jianji.note.data.Note
import com.jianji.note.data.NoteSort
import org.junit.Assert.assertEquals
import org.junit.Test

class NoteSortTest {

    private fun note(
        id: Long,
        created: Long,
        updated: Long,
        pinned: Boolean = false,
    ) = Note(
        id = id,
        title = "n$id",
        pinned = pinned,
        createdAt = created,
        updatedAt = updated,
    )

    private val notes = listOf(
        note(id = 1, created = 100, updated = 500),
        note(id = 2, created = 200, updated = 300),
        note(id = 3, created = 300, updated = 100, pinned = true),
    )

    @Test
    fun `默认按修改时间倒序且置顶优先`() {
        val sorted = NoteSort.sort(notes, NoteSort.UPDATED_DESC)
        assertEquals(listOf(3L, 1L, 2L), sorted.map { it.id })
    }

    @Test
    fun `按创建时间倒序`() {
        val sorted = NoteSort.sort(notes, NoteSort.CREATED_DESC)
        assertEquals(listOf(3L, 2L, 1L), sorted.map { it.id })
    }

    @Test
    fun `按创建时间正序时置顶依然在最前`() {
        val sorted = NoteSort.sort(notes, NoteSort.CREATED_ASC)
        // id=3 置顶所以在最前，其余按创建时间从旧到新
        assertEquals(listOf(3L, 1L, 2L), sorted.map { it.id })
    }
}
