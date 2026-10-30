package com.dinlipi.app

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/** Time helpers. Minutes are counted from midnight; days are keyed as yyyyMMdd. */
object Time {
    private val dayNames = arrayOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    fun now(): Calendar = Calendar.getInstance()

    fun startOfToday(): Calendar = now().apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

    fun dayKey(c: Calendar = now()): Int =
        c.get(Calendar.YEAR) * 10000 + (c.get(Calendar.MONTH) + 1) * 100 + c.get(Calendar.DAY_OF_MONTH)

    fun minuteOfDay(c: Calendar = now()): Int =
        c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)

    /** Bit for the weekday: Sunday = bit 0 ... Saturday = bit 6. */
    fun dayBit(c: Calendar = now()): Int = 1 shl (c.get(Calendar.DAY_OF_WEEK) - 1)

    /** Millis at [minute] past today's midnight. */
    fun todayAt(minute: Int): Long = startOfToday().timeInMillis + minute * 60_000L

    fun clock(minute: Int): String {
        val m = ((minute % 1440) + 1440) % 1440
        return String.format(Locale.US, "%02d:%02d", m / 60, m % 60)
    }

    fun length(min: Int): String = when {
        min < 60 -> "$min min"
        min % 60 == 0 -> "${min / 60} h"
        else -> "${min / 60} h ${min % 60} min"
    }

    fun countdown(ms: Long): String {
        val s = if (ms <= 0) 0 else (ms + 999) / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%02d:%02d", m, sec)
    }

    fun dateLabel(): String = SimpleDateFormat("EEEE, d MMM", Locale.getDefault()).format(Date())

    fun daysText(days: Int): String =
        if (days == 127) "Every day"
        else dayNames.indices.filter { days and (1 shl it) != 0 }.joinToString(" ") { dayNames[it] }
}
