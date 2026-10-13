package com.jianji.note.util

import android.content.Context
import android.net.Uri
import java.io.File

/**
 * 应用私有目录里的用户素材：插入的图片、自定义背景、自定义字体。
 * 都从系统文件选择器拿 Uri，复制进私有目录后再使用，这样不会因为外部文件被移动而失效。
 */
object AppFiles {

    const val KIND_IMAGE = "images"
    const val KIND_BACKGROUND = "backgrounds"
    const val KIND_FONT = "fonts"

    fun dir(context: Context, kind: String): File =
        File(context.filesDir, kind).apply { if (!exists()) mkdirs() }

    /** 复制进私有目录，返回落盘后的文件名；失败返回 null。 */
    fun import(context: Context, uri: Uri, kind: String): String? = try {
        val extension = extensionOf(context.contentResolver.getType(uri))
        val name = "${System.currentTimeMillis()}_${(1000..9999).random()}.$extension"
        context.contentResolver.openInputStream(uri)?.use { input ->
            File(dir(context, kind), name).outputStream().use { output -> input.copyTo(output) }
        }
        name
    } catch (e: Exception) {
        null
    }

    fun file(context: Context, kind: String, name: String): File = File(dir(context, kind), name)

    fun delete(context: Context, kind: String, name: String) {
        runCatching { file(context, kind, name).delete() }
    }

    /** 字体文件必须能在本地按路径加载，所以只支持 ttf / otf。 */
    fun isFontFile(context: Context, name: String): Boolean {
        val file = file(context, KIND_FONT, name)
        return file.exists() && (name.endsWith(".ttf", true) || name.endsWith(".otf", true))
    }

    private fun extensionOf(mime: String?): String = when {
        mime == null -> "bin"
        mime.contains("png") -> "png"
        mime.contains("jpeg") || mime.contains("jpg") -> "jpg"
        mime.contains("webp") -> "webp"
        mime.contains("gif") -> "gif"
        mime.contains("ttf") -> "ttf"
        mime.contains("otf") -> "otf"
        else -> "bin"
    }
}
