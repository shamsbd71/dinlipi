package com.dinlipi.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView

/** Countdown for one block: Pause, +5 min, Done. Redraws once a second only while on screen. */
class TimerActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var block: Block
    private lateinit var timeText: TextView
    private lateinit var subText: TextView
    private lateinit var progress: TimerProgress
    private lateinit var pauseBtn: Button

    private val handler = Handler(Looper.getMainLooper())
    private val tick = object : Runnable {
        override fun run() {
            show()
            handler.postDelayed(this, 1000)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        load(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        load(intent)
    }

    private fun load(i: Intent) {
        val id = i.getLongExtra(Alarms.EXTRA_ID, -1)
        block = Db.get(this).block(id) ?: run { finish(); return }
        ensureTimer()
        build()
    }

    override fun onResume() {
        super.onResume()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    /** Start a timer for this block unless one is already running for it today. */
    private fun ensureTimer() {
        val day = Time.dayKey()
        if (Prefs.timerBlock(this) == block.id && Prefs.timerDay(this) == day) return
        val item = Db.get(this).today().firstOrNull { it.block.id == block.id }
        val nowMin = Time.minuteOfDay()
        val now = System.currentTimeMillis()
        val endsAt = if (item != null && nowMin >= item.start && nowMin < item.end) Time.todayAt(item.end)
        else now + block.lengthMin * 60_000L
        Prefs.setTimer(this, block.id, day, endsAt, -1)
        Db.get(this).setStatus(day, block.id, STARTED)
        Alarms.scheduleTimerEnd(this, block.id, endsAt)
        Alarms.dismiss(this, block.id)
    }

    private fun left(): Long {
        val paused = Prefs.timerPausedLeft(this)
        return if (paused >= 0) paused else Prefs.timerEndsAt(this) - System.currentTimeMillis()
    }

    private fun build() {
        ui = Ui(this)
        val item = Db.get(this).today().firstOrNull { it.block.id == block.id }
        val start = item?.start ?: block.startMin
        val root = ui.col().apply { setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(20)) }

        val top = ui.row()
        top.addView(ui.iconButton(R.drawable.ic_back, "Back", if (ui.isFun) Ui.WHITE else ui.bg, ui.ink) { finish() })
        val range = "${Time.clock(start)} – ${Time.clock(start + block.lengthMin)}"
        top.addView(
            if (ui.isFun) ui.chip(range, Ui.PURPLE) else ui.text(range, 14f, ui.sub),
            ui.lp(WRAP, WRAP, l = 12)
        )
        root.addView(top, ui.lp())

        root.addView(
            ui.text(block.name, if (ui.isFun) 30f else 26f, ui.ink, true).apply {
                if (!ui.isFun) gravity = Gravity.CENTER_HORIZONTAL
            },
            ui.lp(t = 20)
        )

        timeText = ui.text("", if (ui.isFun) 72f else 56f, ui.ink, true).apply { gravity = Gravity.CENTER }
        subText = ui.text("", if (ui.isFun) 16f else 14f, if (ui.isFun) Ui.INK else ui.sub).apply { gravity = Gravity.CENTER }
        progress = TimerProgress(this, ui.isFun)

        if (ui.isFun) {
            val card = ui.card(Ui.BLUE, Ui.PINK, 20)
            card.gravity = Gravity.CENTER_HORIZONTAL
            card.addView(timeText, ui.lp())
            card.addView(progress, ui.lp(MATCH, ui.dp(28), t = 14, b = 14))
            card.addView(subText, ui.lp())
            root.addView(card, ui.lp(t = 22))
        } else {
            val ring = FrameLayout(this)
            ring.addView(progress, FrameLayout.LayoutParams(MATCH, MATCH))
            val center = ui.col(timeText, subText).apply { gravity = Gravity.CENTER }
            ring.addView(center, FrameLayout.LayoutParams(MATCH, MATCH))
            root.addView(ring, ui.lp(ui.dp(250), ui.dp(250), t = 32).apply { gravity = Gravity.CENTER_HORIZONTAL })
        }

        val buttons = ui.row()
        pauseBtn = ui.secondary("Pause", 52) { togglePause() }
        val plus = if (ui.isFun) ui.button("+5 min", Ui.ORANGE, Ui.INK, Ui.INK, Ui.INK, 52, 17f) { addFive() }
        else ui.secondary("+5 min", 52) { addFive() }
        buttons.addView(pauseBtn, ui.lp(0, WRAP, 1f, r = 7))
        buttons.addView(plus, ui.lp(0, WRAP, 1f, l = 7))
        root.addView(buttons, ui.lp(t = 32))
        root.addView(ui.primary(if (ui.isFun) "Done!" else "Done", 56) { done() }, ui.lp(t = 14))

        root.addView(ui.space(1f))

        val next = Db.get(this).today().firstOrNull { it.start > start && it.block.id != block.id && !it.isClosed }
        if (next != null) {
            val card = ui.card(Ui.WHITE, Ui.YELLOW, 14)
            val r = ui.row()
            r.addView(ui.text(if (ui.isFun) "Up next" else "Next", 14f, ui.sub, ui.isFun), ui.lp(0, WRAP, 1f))
            r.addView(ui.text("${Time.clock(next.start)} · ${next.block.name}", 16f, ui.ink, ui.isFun))
            card.addView(r)
            root.addView(card, ui.lp())
        }

        ui.setContent(root)
        show()
    }

    private fun show() {
        if (!::timeText.isInitialized) return
        val l = left()
        val paused = Prefs.timerPausedLeft(this) >= 0
        timeText.text = Time.countdown(l)
        val total = maxOf(block.lengthMin * 60_000L, l)
        progress.fraction = if (total > 0) l.toFloat() / total else 0f
        subText.text = when {
            l <= 0 -> "Time's up!"
            paused -> "Paused"
            ui.isFun -> "left of ${Time.length(block.lengthMin)} · keep going!"
            else -> "left of ${Time.length(block.lengthMin)}"
        }
        pauseBtn.text = if (paused) "Resume" else "Pause"
    }

    private fun togglePause() {
        val day = Prefs.timerDay(this)
        val paused = Prefs.timerPausedLeft(this)
        if (paused >= 0) {
            val endsAt = System.currentTimeMillis() + paused
            Prefs.setTimer(this, block.id, day, endsAt, -1)
            Alarms.scheduleTimerEnd(this, block.id, endsAt)
        } else {
            Prefs.setTimer(this, block.id, day, Prefs.timerEndsAt(this), maxOf(0L, left()))
            Alarms.cancelTimerEnd(this)
        }
        show()
    }

    private fun addFive() {
        val day = Prefs.timerDay(this)
        val paused = Prefs.timerPausedLeft(this)
        val five = 5 * 60_000L
        if (paused >= 0) {
            Prefs.setTimer(this, block.id, day, Prefs.timerEndsAt(this), paused + five)
        } else {
            val endsAt = maxOf(Prefs.timerEndsAt(this), System.currentTimeMillis()) + five
            Prefs.setTimer(this, block.id, day, endsAt, -1)
            Alarms.scheduleTimerEnd(this, block.id, endsAt)
        }
        show()
    }

    private fun done() {
        Db.get(this).setStatus(Time.dayKey(), block.id, DONE)
        Prefs.clearTimer(this)
        Alarms.cancelTimerEnd(this)
        Alarms.scheduleAll(this)
        finish()
    }
}
