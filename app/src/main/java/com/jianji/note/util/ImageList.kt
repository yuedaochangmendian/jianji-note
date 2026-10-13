package com.jianji.note.util

import org.json.JSONArray

/**
 * 笔记里的图片文件名列表，以 JSON 数组字符串存在 notes.images 列。
 * 纯字符串处理，方便单元测试。
 */
object ImageList {

    fun encode(names: List<String>): String {
        val cleaned = names.map { it.trim() }.filter { it.isNotEmpty() }
        if (cleaned.isEmpty()) return ""
        return JSONArray(cleaned).toString()
    }

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            (0 until array.length())
                .mapNotNull { array.optString(it) }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun append(raw: String?, name: String): String = encode(decode(raw) + name)

    fun remove(raw: String?, name: String): String = encode(decode(raw).filterNot { it == name })
}
