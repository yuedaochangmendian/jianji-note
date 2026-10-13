package com.jianji.note.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "folders")
data class Folder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val name: String,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
)
