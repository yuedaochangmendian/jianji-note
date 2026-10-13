package com.jianji.note.ui

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.jianji.note.data.Note
import java.io.File

/**
 * 用系统自带的 PdfDocument 生成单页/多页 PDF，不引第三方库。
 * 系统默认字体内置中文字形，所以中文能直接画出来。
 */
object PdfExporter {

    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val TITLE_SIZE = 18f
    private const val BODY_SIZE = 12f
    private const val LINE_GAP = 6f

    fun write(file: File, note: Note, contentSizeSp: Float): Boolean = try {
        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
        )
        var canvas = page.canvas
        var y = MARGIN

        fun newPageIfNeeded(needed: Float) {
            if (y + needed <= PAGE_HEIGHT - MARGIN) return
            document.finishPage(page)
            pageNumber++
            page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create(),
            )
            canvas = page.canvas
            y = MARGIN
        }

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = TITLE_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = contentSizeSp.coerceIn(11f, 16f)
        }
        val maxWidth = PAGE_WIDTH - MARGIN * 2

        wrap(note.displayTitle, titlePaint, maxWidth).forEach { line ->
            newPageIfNeeded(TITLE_SIZE + LINE_GAP)
            canvas.drawText(line, MARGIN, y + TITLE_SIZE, titlePaint)
            y += TITLE_SIZE + LINE_GAP
        }
        y += LINE_GAP * 2

        note.content.replace("\r\n", "\n").split("\n").forEach { rawLine ->
            val line = rawLine.ifBlank { " " }
            wrap(line, bodyPaint, maxWidth).forEach { piece ->
                newPageIfNeeded(bodyPaint.textSize + LINE_GAP)
                canvas.drawText(piece, MARGIN, y + bodyPaint.textSize, bodyPaint)
                y += bodyPaint.textSize + LINE_GAP
            }
        }

        document.finishPage(page)
        file.outputStream().use { document.writeTo(it) }
        document.close()
        true
    } catch (e: Exception) {
        false
    }

    /** 中文没有空格，按字符宽度断行。 */
    private fun wrap(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        val builder = StringBuilder()
        text.forEach { char ->
            val candidate = builder.toString() + char
            if (paint.measureText(candidate) > maxWidth && builder.isNotEmpty()) {
                lines += builder.toString()
                builder.setLength(0)
            }
            builder.append(char)
        }
        if (builder.isNotEmpty()) lines += builder.toString()
        return lines
    }
}
