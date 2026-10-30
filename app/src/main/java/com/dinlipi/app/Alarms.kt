package com.dinlipi.app

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import java.util.Calendar

/**
 * Schedules one exact alarm per block (its next start), plus snoozes and the timer's end.
 * Everything runs on the phone through AlarmManager; no internet, no background service.
 */
object Alarms {
    const val ACTION_BLOCK = "com.dinlipi.app.BLOCK"
    const val ACTION_SNOOZE = "com.dinlipi.app.SNOOZE"
    const val ACTION_TIMER = "com.dinlipi.app.TIMER_END"
    const val EXTRA_ID = "id"

    private const val CH_ALARM = "alarms"
    private const val CH_REMIND = "reminders"
    private const val CH_TIMER = "timer"

    private const val SNOOZE_BASE = 100_000
    private const val TIMER_REQ = 200_000
    const val TIMER_NOTIF = 300_000

    private val IMMUTABLE = if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0
    private const val UPDATE = PendingIntent.FLAG_UPDATE_CURRENT

    private val vibration = longArrayOf(0, 700, 500, 700, 500, 700)

    private fun am(c: Context) = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private fun nm(c: Context) = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    private fun alarmSound(): Uri? =
        RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

    private val alarmAudio: AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ALARM)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
        .build()

    fun createChannels(c: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val alarms = NotificationChannel(CH_ALARM, "Alarms", NotificationManager.IMPORTANCE_HIGH).apply {
            setSound(alarmSound(), alarmAudio)
            enableVibration(true)
            vibrationPattern = vibration
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        val reminders = NotificationChannel(CH_REMIND, "Reminders", NotificationManager.IMPORTANCE_HIGH)
        val timer = NotificationChannel(CH_TIMER, "Timer", NotificationManager.IMPORTANCE_HIGH)
        nm(c).createNotificationChannels(listOf(alarms, reminders, timer))
    }

    fun canExact(c: Context): Boolean = Build.VERSION.SDK_INT < 31 || am(c).canScheduleExactAlarms()

    fun notificationsOn(c: Context): Boolean = Build.VERSION.SDK_INT < 24 || nm(c).areNotificationsEnabled()

    fun canFullScreen(c: Context): Boolean = Build.VERSION.SDK_INT < 34 || nm(c).canUseFullScreenIntent()

    private fun broadcast(c: Context, action: String, id: Long, req: Int): PendingIntent =
        PendingIntent.getBroadcast(
            c, req,
            Intent(c, AlarmReceiver::class.java).setAction(action).putExtra(EXTRA_ID, id),
            UPDATE or IMMUTABLE
        )

    fun openApp(c: Context): PendingIntent =
        PendingIntent.getActivity(c, 0, Intent(c, MainActivity::class.java), UPDATE or IMMUTABLE)

    @SuppressLint("ScheduleExactAlarm") // guarded by canExact()
    private fun setAt(c: Context, at: Long, pi: PendingIntent, alarmClock: Boolean) {
        val am = am(c)
        when {
            !canExact(c) ->
                if (Build.VERSION.SDK_INT >= 23) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
                else am.set(AlarmManager.RTC_WAKEUP, at, pi)
            alarmClock -> am.setAlarmClock(AlarmManager.AlarmClockInfo(at, openApp(c)), pi)
            Build.VERSION.SDK_INT >= 23 -> am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi)
            else -> am.setExact(AlarmManager.RTC_WAKEUP, at, pi)
        }
    }

    /** Next time this block should ring, looking up to a week ahead. */
    private fun nextTrigger(db: Db, b: Block): Long? {
        val now = System.currentTimeMillis()
        for (offset in 0..7) {
            val cal = Time.startOfToday().apply { add(Calendar.DAY_OF_YEAR, offset) }
            if (b.days and Time.dayBit(cal) == 0) continue
            val day = Time.dayKey(cal)
            val start = if (offset == 0) {
                if (db.status(day, b.id) != null) continue // already started, done or skipped today
                db.startOn(b, db.shifts(day))
            } else b.startMin
            val at = cal.timeInMillis + start * 60_000L
            if (at > now) return at
        }
        return null
    }

    /** (Re)schedule every block. Cheap: one AlarmManager call per block. */
    fun scheduleAll(c: Context) {
        val db = Db.get(c)
        for (b in db.blocks()) {
            val pi = broadcast(c, ACTION_BLOCK, b.id, b.id.toInt())
            am(c).cancel(pi)
            val at = nextTrigger(db, b) ?: continue
            setAt(c, at, pi, b.alarm)
        }
    }

    fun cancel(c: Context, id: Long) {
        am(c).cancel(broadcast(c, ACTION_BLOCK, id, id.toInt()))
        am(c).cancel(broadcast(c, ACTION_SNOOZE, id, SNOOZE_BASE + id.toInt()))
        dismiss(c, id)
    }

    fun snooze(c: Context, id: Long) {
        setAt(c, System.currentTimeMillis() + 5 * 60_000L, broadcast(c, ACTION_SNOOZE, id, SNOOZE_BASE + id.toInt()), true)
    }

    fun scheduleTimerEnd(c: Context, id: Long, at: Long) {
        setAt(c, at, broadcast(c, ACTION_TIMER, id, TIMER_REQ), false)
    }

    fun cancelTimerEnd(c: Context) {
        am(c).cancel(broadcast(c, ACTION_TIMER, 0, TIMER_REQ))
        nm(c).cancel(TIMER_NOTIF)
    }

    fun dismiss(c: Context, id: Long) = nm(c).cancel(id.toInt())

    @Suppress("DEPRECATION")
    private fun builder(c: Context, channel: String): Notification.Builder =
        if (Build.VERSION.SDK_INT >= 26) Notification.Builder(c, channel)
        else Notification.Builder(c).setPriority(Notification.PRIORITY_HIGH)

    /** Full-screen alarm that keeps ringing until the person responds (or 10 minutes pass). */
    @SuppressLint("MissingPermission") // silently skipped if notifications are off
    @Suppress("DEPRECATION")
    fun showAlarm(c: Context, b: Block, startMin: Int) {
        val screen = PendingIntent.getActivity(
            c, b.id.toInt(),
            Intent(c, AlarmActivity::class.java)
                .putExtra(EXTRA_ID, b.id)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION),
            UPDATE or IMMUTABLE
        )
        val n = builder(c, CH_ALARM)
            .setSmallIcon(R.drawable.ic_alarm)
            .setContentTitle(b.name)
            .setContentText("${Time.clock(startMin)} · ${Time.length(b.lengthMin)}")
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setFullScreenIntent(screen, true)
            .setContentIntent(screen)
            .setOngoing(true)
        if (Build.VERSION.SDK_INT >= 26) n.setTimeoutAfter(10 * 60_000L)
        else n.setSound(alarmSound(), alarmAudio).setVibrate(vibration)
        val notification = n.build()
        notification.flags = notification.flags or Notification.FLAG_INSISTENT
        nm(c).notify(b.id.toInt(), notification)
    }

    @SuppressLint("MissingPermission") // silently skipped if notifications are off
    @Suppress("DEPRECATION")
    fun showReminder(c: Context, b: Block, startMin: Int) {
        val n = builder(c, CH_REMIND)
            .setSmallIcon(R.drawable.ic_today)
            .setContentTitle("Time for ${b.name}")
            .setContentText("${Time.clock(startMin)} · ${Time.length(b.lengthMin)}")
            .setContentIntent(openApp(c))
            .setAutoCancel(true)
        if (Build.VERSION.SDK_INT < 26) n.setDefaults(Notification.DEFAULT_ALL)
        nm(c).notify(b.id.toInt(), n.build())
    }

    @SuppressLint("MissingPermission") // silently skipped if notifications are off
    @Suppress("DEPRECATION")
    fun showTimerEnd(c: Context, b: Block) {
        val open = PendingIntent.getActivity(
            c, TIMER_REQ,
            Intent(c, TimerActivity::class.java).putExtra(EXTRA_ID, b.id),
            UPDATE or IMMUTABLE
        )
        val n = builder(c, CH_TIMER)
            .setSmallIcon(R.drawable.ic_check)
            .setContentTitle("Time's up: ${b.name}")
            .setContentText("Tap to mark it done")
            .setContentIntent(open)
            .setAutoCancel(true)
        if (Build.VERSION.SDK_INT < 26) n.setDefaults(Notification.DEFAULT_ALL)
        nm(c).notify(TIMER_NOTIF, n.build())
    }
}
