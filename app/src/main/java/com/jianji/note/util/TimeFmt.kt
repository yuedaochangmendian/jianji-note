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

    /** 云同步时间这类需要精确到分钟的场景。 */
    fun fullDateTime(millis: Long): String {
        if (millis <= 0L) return ""
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(millis))
    }

    /** 「日历」列表样式左侧的大号日期。 */
    fun dayOfMonth(millis: Long): String =
        SimpleDateFormat("dd", Locale.getDefault()).format(Date(millis))

    /** 「日历」列表样式左侧的月份标签，中文下是「10月」，英文下是「Oct」。 */
    fun monthLabel(millis: Long): String =
        SimpleDateFormat("MMM", Locale.getDefault()).format(Date(millis))

    private fun sameDay(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
            a.get(Calendar.DAY_OF_YEAR) == b.get(Calendar.DAY_OF_YEAR)
}
