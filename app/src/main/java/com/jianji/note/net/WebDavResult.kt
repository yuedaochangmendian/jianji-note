package com.jianji.note.net

sealed class WebDavResult<out T> {
    data class Success<T>(val data: T) : WebDavResult<T>()
    data class Error(val message: String, val httpCode: Int = 0) : WebDavResult<Nothing>()
}
