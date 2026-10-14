package com.jianji.note.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.widget.ImageView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 缩略图加载：先读尺寸再按需降采样，避免大图直接解码把内存撑爆。
 * 解码放 IO 线程——预览页是按屏幕宽度解的，大图在主线程解会卡。
 */
object ImageLoader {

    suspend fun loadThumbnail(imageView: ImageView, file: File, targetPx: Int) {
        if (!file.exists()) {
            imageView.setImageDrawable(null)
            return
        }
        val bitmap = withContext(Dispatchers.IO) { decodeSampled(file, targetPx) }
        imageView.setImageBitmap(bitmap)
    }

    fun decodeSampled(file: File, targetPx: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (bounds.outWidth / (sample * 2) >= targetPx && bounds.outHeight / (sample * 2) >= targetPx) {
            sample *= 2
        }
        return runCatching {
            BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply {
                inSampleSize = sample
            })
        }.getOrNull()
    }
}
