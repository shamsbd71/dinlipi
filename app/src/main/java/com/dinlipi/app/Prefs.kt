package com.dinlipi.app

import android.content.Context
import android.content.SharedPreferences

/** Small settings: the user's name, the theme, the running timer. */
object Prefs {
    private fun p(c: Context): SharedPreferences =
        c.getSharedPreferences("dinlipi", Context.MODE_PRIVATE)

    fun name(c: Context): String = p(c).getString("name", "") ?: ""
    fun setName(c: Context, v: String) = p(c).edit().putString("name", v).apply()

    fun isFun(c: Context): Boolean = p(c).getString("theme", "calm") == "fun"
    fun setFun(c: Context, on: Boolean) =
        p(c).edit().putString("theme", if (on) "fun" else "calm").apply()

    fun isDismissed(c: Context, key: String): Boolean = p(c).getString("dismissed", "") == key
    fun dismiss(c: Context, key: String) = p(c).edit().putString("dismissed", key).apply()

    fun askedNotifications(c: Context): Boolean = p(c).getBoolean("asked_notif", false)
    fun setAskedNotifications(c: Context) = p(c).edit().putBoolean("asked_notif", true).apply()

    // Running timer: which block, which day, when it ends, and time left while paused (-1 = running).
    fun timerBlock(c: Context): Long = p(c).getLong("t_block", -1)
    fun timerDay(c: Context): Int = p(c).getInt("t_day", 0)
    fun timerEndsAt(c: Context): Long = p(c).getLong("t_end", 0)
    fun timerPausedLeft(c: Context): Long = p(c).getLong("t_paused", -1)

    fun setTimer(c: Context, block: Long, day: Int, endsAt: Long, pausedLeft: Long) =
        p(c).edit()
            .putLong("t_block", block)
            .putInt("t_day", day)
            .putLong("t_end", endsAt)
            .putLong("t_paused", pausedLeft)
            .apply()

    fun clearTimer(c: Context) =
        p(c).edit().remove("t_block").remove("t_day").remove("t_end").remove("t_paused").apply()
}
