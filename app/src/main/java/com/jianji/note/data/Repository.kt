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

    /**
     * 用备份内容整体替换本机数据（云同步恢复用）。
     * folders 里是「备份中的原 id → 文件夹」，notes 里是「备份中的原文件夹 id → 笔记」，
     * 重新插入时文件夹会拿到新 id，因此笔记的 folderId 需要按映射改写。
     */
    suspend fun replaceAll(
        folders: List<Pair<Long, Folder>>,
        notes: List<Pair<Long?, Note>>,
    ): Int = db.withTransaction {
        noteDao.loadAll().forEach { noteDao.deleteById(it.id) }
        folderDao.loadAll().forEach { folderDao.delete(it) }

        val idMap = HashMap<Long, Long>()
        folders.forEach { (oldId, folder) ->
            idMap[oldId] = folderDao.insert(folder)
        }
        val imported = notes.map { (oldFolderId, note) ->
            note.copy(folderId = oldFolderId?.let { idMap[it] })
        }
        noteDao.insertAll(imported)
        imported.size
    }
}
