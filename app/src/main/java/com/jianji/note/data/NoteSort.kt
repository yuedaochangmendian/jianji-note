package com.jianji.note.data

/**
 * 列表排序。三种顺序都保持「置顶排在最前」，这是原版的行为。
 * 抽成纯函数方便单元测试。
 */
object NoteSort {

    const val UPDATED_DESC = 0
    const val CREATED_DESC = 1
    const val CREATED_ASC = 2

    fun comparator(sortBy: Int): Comparator<Note> {
        val byOrder = when (sortBy) {
            CREATED_DESC -> compareByDescending<Note> { it.createdAt }
            CREATED_ASC -> compareBy { it.createdAt }
            else -> compareByDescending { it.updatedAt }
        }
        // 置顶优先，其次按所选顺序，最后用 id 兜底保证顺序稳定
        return compareByDescending<Note> { it.pinned }
            .then(byOrder)
            .thenByDescending { it.id }
    }

    fun sort(notes: List<Note>, sortBy: Int): List<Note> = notes.sortedWith(comparator(sortBy))
}
