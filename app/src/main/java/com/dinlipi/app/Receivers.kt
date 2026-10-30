package com.dinlipi.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Fires when a block starts, a snooze ends, or the timer runs out. */
class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        val id = intent.getLongExtra(Alarms.EXTRA_ID, -1)
        val db = Db.get(c)
        val block = db.block(id) ?: return
        when (intent.action) {
            Alarms.ACTION_BLOCK, Alarms.ACTION_SNOOZE -> {
                val start = db.today().firstOrNull { it.block.id == id }?.start ?: block.startMin
                if (block.alarm) Alarms.showAlarm(c, block, start) else Alarms.showReminder(c, block, start)
                if (intent.action == Alarms.ACTION_BLOCK) Alarms.scheduleAll(c)
            }
            Alarms.ACTION_TIMER -> Alarms.showTimerEnd(c, block)
        }
    }
}

/** Alarms are cleared on reboot or clock changes, so set them again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, intent: Intent) {
        Alarms.createChannels(c)
        Alarms.scheduleAll(c)
    }
}
