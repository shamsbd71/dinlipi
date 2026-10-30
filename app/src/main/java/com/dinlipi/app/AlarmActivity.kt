package com.dinlipi.app

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager

/** Full-screen alarm over the lock screen: Start now, Snooze 5 min, or Skip. */
class AlarmActivity : Activity() {

    private lateinit var ui: Ui

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showOverLockScreen()
        build()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        build()
    }

    @Suppress("DEPRECATION")
    private fun showOverLockScreen() {
        if (Build.VERSION.SDK_INT >= 27) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun build() {
        ui = Ui(this)
        val id = intent.getLongExtra(Alarms.EXTRA_ID, -1)
        val db = Db.get(this)
        val block = db.block(id) ?: run { finish(); return }
        val item = db.today().firstOrNull { it.block.id == id }
        val start = item?.start ?: block.startMin
        val next = db.today().firstOrNull { it.start > start && it.block.id != id }

        val bg = if (ui.isFun) Ui.YELLOW else Ui.TEAL_DARK
        val fg = if (ui.isFun) Ui.INK else Ui.WHITE
        val soft = if (ui.isFun) Ui.INK else 0xFFC8DEDB.toInt()

        val root = ui.col().apply {
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(ui.dp(24), 0, ui.dp(24), ui.dp(32))
        }
        root.addView(ui.space(1f))

        val bell = ui.icon(R.drawable.ic_alarm, fg, 48).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER
            if (ui.isFun) {
                background = ui.box(Ui.PINK, 20, Ui.INK, ui.dp(3), Ui.INK, ui.dp(6))
                setPadding(0, 0, ui.dp(6), ui.dp(6))
                rotation = -6f
            }
        }
        val bellSize = if (ui.isFun) ui.dp(88) + ui.dp(6) else ui.dp(56)
        root.addView(bell, ui.lp(bellSize, bellSize))

        val clock = ui.text(Time.clock(start), if (ui.isFun) 64f else 76f, fg, ui.isFun).apply {
            gravity = Gravity.CENTER
            if (ui.isFun) {
                background = ui.box(Ui.WHITE, 18, Ui.INK, ui.dp(3), Ui.BLUE, ui.dp(6))
                setPadding(ui.dp(24), ui.dp(4), ui.dp(24) + ui.dp(6), ui.dp(4) + ui.dp(6))
            } else {
                typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
            }
        }
        root.addView(clock, ui.lp(WRAP, WRAP, t = 24))

        root.addView(
            ui.text(if (ui.isFun) "Time for ${block.name}!" else block.name, if (ui.isFun) 30f else 26f, fg, true).apply {
                gravity = Gravity.CENTER
            },
            ui.lp(WRAP, WRAP, t = 20)
        )
        val detail = Time.length(block.lengthMin) + if (next != null) " · then ${next.block.name} at ${Time.clock(next.start)}" else ""
        root.addView(ui.text(detail, 16f, soft).apply { gravity = Gravity.CENTER }, ui.lp(WRAP, WRAP, t = 6))

        root.addView(ui.space(1.3f))

        val startNow = {
            Alarms.dismiss(this, id)
            startActivity(Intent(this, TimerActivity::class.java).putExtra(Alarms.EXTRA_ID, id))
            finish()
        }
        val snooze = {
            Alarms.dismiss(this, id)
            Alarms.snooze(this, id)
            finish()
        }
        val skip = {
            Alarms.dismiss(this, id)
            db.setStatus(Time.dayKey(), id, SKIPPED)
            Alarms.scheduleAll(this)
            finish()
        }

        if (ui.isFun) {
            root.addView(ui.button("Let's go!", Ui.GREEN, Ui.INK, Ui.INK, Ui.INK, 60, 20f, startNow), ui.lp())
            root.addView(ui.button("Snooze 5 min", Ui.WHITE, Ui.INK, Ui.INK, Ui.INK, 56, 18f, snooze), ui.lp(t = 14))
        } else {
            root.addView(ui.button("Start now", Ui.WHITE, Ui.TEAL_DARK, height = 60, size = 18f, onClick = startNow), ui.lp())
            root.addView(ui.button("Snooze 5 min", 0, Ui.WHITE, soft, 0, 60, 18f, snooze), ui.lp(t = 12))
        }
        root.addView(ui.button("Skip this block", 0, soft, height = 44, size = 15f, onClick = skip), ui.lp(t = 8))

        ui.setContent(root, bg, lightBars = ui.isFun)
    }
}
