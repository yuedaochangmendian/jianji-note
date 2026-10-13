package com.jianji.note.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextPaint
import com.jianji.note.R
import com.jianji.note.data.Note
import com.jianji.note.util.TimeFmt

/** 把笔记渲染成一张适合分享的长图。 */
object ShareImageRenderer {

    private const val WIDTH = 1080
    private const val PADDING = 80f
    private const val MAX_LINES = 40

    fun render(context: Context, note: Note): Bitmap {
        val titlePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#212121")
            textSize = 54f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#424242")
            textSize = 38f
        }
        val footerPaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.parseColor("#9E9E9E")
            textSize = 30f
        }

        val maxWidth = WIDTH - PADDING * 2
        val titleLines = wrap(note.displayTitle, titlePaint, maxWidth).take(3)
        val bodyLines = wrap(note.content.replace("\r\n", "\n"), bodyPaint, maxWidth).take(MAX_LINES)
        val footer = context.getString(R.string.share_image_footer, TimeFmt.friendly(note.createdAt))

        val titleHeight = titleLines.size * (titlePaint.textSize * 1.4f)
        val bodyHeight = bodyLines.size * (bodyPaint.textSize * 1.6f)
        val height = (PADDING * 2 + titleHeight + 24f + bodyHeight + 48f + footerPaint.textSize)
            .toInt()
            .coerceAtLeast(600)

        val bitmap = Bitmap.createBitmap(WIDTH, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.WHITE)

        // 顶部一道强调色
        canvas.drawRect(
            RectF(0f, 0f, WIDTH.toFloat(), 12f),
            Paint().apply { color = Color.parseColor("#3F51B5") },
        )

        var y = PADDING + titlePaint.textSize
        titleLines.forEach { line ->
            canvas.drawText(line, PADDING, y, titlePaint)
            y += titlePaint.textSize * 1.4f
        }
        y += 24f
        bodyLines.forEach { line ->
            canvas.drawText(line, PADDING, y, bodyPaint)
            y += bodyPaint.textSize * 1.6f
        }
        canvas.drawText(footer, PADDING, height - PADDING + footerPaint.textSize, footerPaint)
        return bitmap
    }

    private fun wrap(text: String, paint: TextPaint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return emptyList()
        val lines = mutableListOf<String>()
        val builder = StringBuilder()
        text.split("\n").forEach { paragraph ->
            if (paragraph.isEmpty()) {
                lines += ""
                return@forEach
            }
            paragraph.forEach { char ->
                val candidate = builder.toString() + char
                if (paint.measureText(candidate) > maxWidth && builder.isNotEmpty()) {
                    lines += builder.toString()
                    builder.setLength(0)
                }
                builder.append(char)
            }
            lines += builder.toString()
            builder.setLength(0)
        }
        return lines
    }
}
