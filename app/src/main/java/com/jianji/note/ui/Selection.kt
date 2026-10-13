package com.jianji.note.ui

/** 当前列表视图：全部 / 未分类 / 某个文件夹。 */
sealed interface Selection {
    data object All : Selection
    data object Uncategorized : Selection
    data class InFolder(val id: Long) : Selection
}
