package com.jianji.note.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "notes")
data class Note(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val title: String = "",
    val content: String = "",
    val folderId: Long? = null,
    val pinned: Boolean = false,
    /** 单条笔记锁定，打开/删除前需要输入应用密码。 */
    @ColumnInfo(defaultValue = "0") val locked: Boolean = false,
    /** true 表示正文按 Markdown 源码保存，预览时渲染。 */
    @ColumnInfo(defaultValue = "0") val markdown: Boolean = false,
    /** 内嵌图片的文件名列表（JSON 数组字符串），文件存在 filesDir/images 下。 */
    @ColumnInfo(defaultValue = "") val images: String = "",
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
