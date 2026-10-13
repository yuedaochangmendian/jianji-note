package com.jianji.note.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFmt {

    private val timeOnly = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val monthDay = SimpleDateFormat("MM-dd", Locale.getDefault())
    private val full = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun friendly(millis: Long): String {
        if (millis <= 0L) return ""
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply { timeInMillis = millis }
        return when {
            sameDay(now, target) -> timeOnly.format(Date(millis))
            now.get(Calendar.YEAR) == target.get(Calendar.YEAR) -> monthDay.format(Date(millis))
            else -> full.format(Date(millis))
        }
    }

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
