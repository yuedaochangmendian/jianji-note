package com.jianji.note.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface FolderDao {

    @Query("SELECT * FROM folders ORDER BY position ASC, id ASC")
    fun observeAll(): Flow<List<Folder>>

    @Query("SELECT * FROM folders ORDER BY position ASC, id ASC")
    suspend fun loadAll(): List<Folder>

    @Insert
    suspend fun insert(folder: Folder): Long

    @Insert
    suspend fun insertAll(folders: List<Folder>)

    @Update
    suspend fun update(folder: Folder)

    @Delete
    suspend fun delete(folder: Folder)

    @Query("UPDATE notes SET folderId = NULL WHERE folderId = :folderId")
    suspend fun detachNotes(folderId: Long)
}
