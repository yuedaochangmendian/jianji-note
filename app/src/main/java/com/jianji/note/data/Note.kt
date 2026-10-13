package com.jianji.note.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val folderId: Long? = null,
    val pinned: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val deletedAt: Long? = null,
) {
    val displayTitle: String
        get() = title.ifBlank {
            content.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().take(40)
        }.ifBlank { "无标题" }

    val preview: String
        get() = content.lineSequence()
            .filter { it.isNotBlank() }
            .drop(1)
            .joinToString("\n")
            .trim()
}
