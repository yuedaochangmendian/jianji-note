package com.jianji.note.data

import androidx.room.withTransaction

class Repository(private val db: AppDatabase) {

    val noteDao: NoteDao get() = db.noteDao()

    val folderDao: FolderDao get() = db.folderDao()

    suspend fun createFolder(name: String): Long {
        val position = folderDao.loadAll().size
        return folderDao.insert(Folder(name = name, position = position))
    }

    suspend fun renameFolder(folder: Folder, name: String) {
        folderDao.update(folder.copy(name = name))
    }

    /** 删除文件夹时把其中的笔记移到「未分类」，不连带删除笔记。 */
    suspend fun deleteFolder(folder: Folder) = db.withTransaction {
        folderDao.detachNotes(folder.id)
        folderDao.delete(folder)
    }

    suspend fun saveNote(note: Note) {
        if (note.id == 0L) noteDao.insert(note) else noteDao.update(note)
    }

    suspend fun clearAllData() = db.withTransaction {
        noteDao.emptyTrash()
        noteDao.loadAll().forEach { noteDao.deleteById(it.id) }
        folderDao.loadAll().forEach { folderDao.delete(it) }
    }
}
