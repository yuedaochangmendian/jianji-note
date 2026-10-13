package com.jianji.note.util

import com.jianji.note.data.Folder
import com.jianji.note.data.Note
import org.json.JSONArray
import org.json.JSONObject

/**
 * 备份文件格式（JSON）：
 * {
 *   "app": "jianji", "version": 1, "exportedAt": 1699999999999,
 *   "folders": [{ "id": 1, "name": "工作", "position": 0, "createdAt": 0 }],
 *   "notes":   [{ "title": "", "content": "", "folderId": 1, "pinned": false,
 *                 "createdAt": 0, "updatedAt": 0, "deletedAt": null }]
 * }
 */
object Backup {

    private const val FORMAT_VERSION = 2

    class Parsed(
        /** 原文件夹 id 与文件夹内容的对应关系 */
        val folders: List<Pair<Long, Folder>>,
        /** 笔记，以及它在原文件里所属的文件夹 id */
        val notes: List<Pair<Long?, Note>>,
    )

    fun encode(notes: List<Note>, folders: List<Folder>): String {
        val root = JSONObject()
        root.put("app", "jianji")
        root.put("version", FORMAT_VERSION)
        root.put("exportedAt", System.currentTimeMillis())

        val folderArray = JSONArray()
        folders.forEach { folder ->
            folderArray.put(
                JSONObject().apply {
                    put("id", folder.id)
                    put("name", folder.name)
                    put("position", folder.position)
                    put("createdAt", folder.createdAt)
                },
            )
        }
        root.put("folders", folderArray)

        val noteArray = JSONArray()
        notes.forEach { note ->
            noteArray.put(
                JSONObject().apply {
                    put("title", note.title)
                    put("content", note.content)
                    put("folderId", note.folderId ?: JSONObject.NULL)
                    put("pinned", note.pinned)
                    put("locked", note.locked)
                    put("markdown", note.markdown)
                    put("createdAt", note.createdAt)
                    put("updatedAt", note.updatedAt)
                    put("deletedAt", note.deletedAt ?: JSONObject.NULL)
                },
            )
        }
        root.put("notes", noteArray)
        return root.toString(2)
    }

    fun decode(json: String): Parsed {
        val root = JSONObject(json)

        val folders = mutableListOf<Pair<Long, Folder>>()
        root.optJSONArray("folders")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                folders += item.optLong("id") to Folder(
                    name = item.optString("name").ifBlank { "未命名" },
                    position = item.optInt("position"),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                )
            }
        }

        val notes = mutableListOf<Pair<Long?, Note>>()
        root.optJSONArray("notes")?.let { array ->
            for (i in 0 until array.length()) {
                val item = array.getJSONObject(i)
                val oldFolderId = if (item.isNull("folderId")) null else item.optLong("folderId")
                notes += oldFolderId to Note(
                    title = item.optString("title"),
                    content = item.optString("content"),
                    pinned = item.optBoolean("pinned"),
                    locked = item.optBoolean("locked"),
                    markdown = item.optBoolean("markdown"),
                    createdAt = item.optLong("createdAt", System.currentTimeMillis()),
                    updatedAt = item.optLong("updatedAt", System.currentTimeMillis()),
                    deletedAt = if (item.isNull("deletedAt")) null else item.optLong("deletedAt"),
                )
            }
        }

        return Parsed(folders, notes)
    }
}
