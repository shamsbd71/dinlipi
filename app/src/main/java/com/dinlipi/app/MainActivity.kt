package com.dinlipi.app

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Home: three tabs (Today, Routine, Settings) swapped inside one light activity. */
class MainActivity : Activity() {

    private lateinit var ui: Ui
    private var tab = TAB_TODAY

    private val handler = Handler(Looper.getMainLooper())
    private var countdownView: TextView? = null
    private var countdownTarget = 0L
    private var renderedMinute = -1

    private val tick = object : Runnable {
        override fun run() {
            if (tab == TAB_TODAY) {
                if (Time.minuteOfDay() != renderedMinute) render()
                else countdownView?.text = Time.countdown(countdownTarget - System.currentTimeMillis())
            }
            handler.postDelayed(this, 1000)
        }
    }

    companion object {
        const val TAB_TODAY = 0
        const val TAB_ROUTINE = 1
        const val TAB_SETTINGS = 2
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (Prefs.name(this).isBlank()) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }
        tab = savedInstanceState?.getInt("tab") ?: TAB_TODAY
        Alarms.createChannels(this)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    override fun onResume() {
        super.onResume()
        if (Prefs.name(this).isBlank()) return
        render()
        Alarms.scheduleAll(this)
        askForNotifications()
        handler.post(tick)
    }

    override fun onPause() {
        super.onPause()
        handler.removeCallbacks(tick)
    }

    private fun askForNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && !Prefs.askedNotifications(this) &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            Prefs.setAskedNotifications(this)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    private fun render() {
        ui = Ui(this)
        countdownView = null
        renderedMinute = Time.minuteOfDay()

        val content = ui.col().apply { setPadding(ui.dp(18), ui.dp(20), ui.dp(18), ui.dp(24)) }
        when (tab) {
            TAB_TODAY -> today(content)
            TAB_ROUTINE -> routine(content)
            else -> settings(content)
        }
        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(content)
        }
        val root = ui.col()
        root.addView(scroll, ui.lp(MATCH, 0, 1f))
        root.addView(nav(), ui.lp())
        ui.setContent(root)
    }

    private fun open(cls: Class<*>, id: Long = -1) {
        startActivity(Intent(this, cls).putExtra(Alarms.EXTRA_ID, id))
    }

    private fun switchTo(t: Int) {
        tab = t
        render()
    }

    // ---------- shared pieces ----------

    private fun header(parent: LinearLayout, small: String?, title: String, vararg actions: View) {
        val left = ui.col()
        if (small != null) {
            left.addView(
                if (ui.isFun) ui.chip(small, Ui.PURPLE) else ui.text(small, 13f, ui.sub),
                ui.lp(WRAP, WRAP, b = 4)
            )
        }
        left.addView(ui.text(title, if (ui.isFun) 30f else 28f, ui.ink, true))
        val r = ui.row()
        r.addView(left, ui.lp(0, WRAP, 1f))
        for (v in actions) {
            (v.layoutParams as? LinearLayout.LayoutParams)?.leftMargin = ui.dp(10)
            r.addView(v)
        }
        parent.addView(r, ui.lp(b = 16))
    }

    private fun addButton() = ui.iconButton(
        R.drawable.ic_plus, "Add a block", if (ui.isFun) Ui.YELLOW else Ui.TEAL, if (ui.isFun) Ui.INK else Ui.WHITE
    ) { open(EditBlockActivity::class.java) }

    private fun avatarButton(): View {
        val name = Prefs.name(this)
        val b = if (ui.isFun) ui.button(name.take(1).uppercase(), Ui.GREEN, Ui.INK, Ui.INK, Ui.INK, 46, 20f) { switchTo(TAB_SETTINGS) }
        else ui.button(name.take(1).uppercase(), ui.divider, ui.ink, 0, 0, 46, 17f) { switchTo(TAB_SETTINGS) }
        b.setPadding(0, 0, ui.lift, ui.lift)
        b.contentDescription = "Settings"
        b.layoutParams = LinearLayout.LayoutParams(ui.dp(46) + ui.lift, ui.dp(46) + ui.lift)
        return b
    }

    private fun nav(): View {
        val items = listOf(
            Triple("Today", R.drawable.ic_today, TAB_TODAY),
            Triple("Routine", R.drawable.ic_list, TAB_ROUTINE),
            Triple("Settings", R.drawable.ic_settings, TAB_SETTINGS),
        )
        if (ui.isFun) {
            val r = ui.row().apply { setPadding(ui.dp(18), ui.dp(8), ui.dp(18), ui.dp(12)) }
            items.forEachIndexed { i, (label, _, t) ->
                val sel = t == tab
                val b = ui.button(label, if (sel) Ui.PINK else Ui.WHITE, Ui.INK, Ui.INK, if (sel) Ui.INK else 0, 46, 15f) { switchTo(t) }
                r.addView(b, ui.lp(0, WRAP, 1f, l = if (i == 0) 0 else 8))
            }
            return r
        }
        val bar = ui.col()
        bar.addView(View(this).apply { setBackgroundColor(ui.divider) }, ui.lp(MATCH, 1))
        val r = ui.row().apply { setBackgroundColor(Ui.WHITE) }
        for ((label, res, t) in items) {
            val color = if (t == tab) Ui.TEAL else ui.sub
            val item = ui.col(
                ui.icon(res, color),
                ui.text(label, 12f, color, t == tab).apply { gravity = Gravity.CENTER }
            ).apply {
                gravity = Gravity.CENTER
                isClickable = true
                isFocusable = true
                setOnClickListener { switchTo(t) }
            }
            r.addView(item, ui.lp(0, ui.dp(64), 1f))
        }
        bar.addView(r, ui.lp())
        return bar
    }

    // ---------- Today ----------

    private fun today(c: LinearLayout) {
        val db = Db.get(this)
        val items = db.today()
        val day = Time.dayKey()
        val nowMin = Time.minuteOfDay()
        val name = Prefs.name(this)

        header(c, Time.dateLabel(), if (ui.isFun) "Hi $name!" else "Hi, $name", avatarButton(), addButton())

        if (items.isEmpty()) {
            val card = ui.card(Ui.WHITE, Ui.BLUE)
            card.addView(ui.text("Nothing planned today", 20f, ui.ink, true), ui.lp(b = 6))
            card.addView(ui.text("Add a block like Wake up, Study or Exercise to start your routine.", 15f, ui.sub), ui.lp(b = 14))
            card.addView(ui.primary("Add a block") { open(EditBlockActivity::class.java) }, ui.lp())
            c.addView(card, ui.lp())
            return
        }

        notificationsCard(c)
        progress(c, items.count { it.status == DONE }, items.size)

        val current = items.firstOrNull { !it.isClosed && nowMin >= it.start && nowMin < it.end }
        val next = items.firstOrNull { it.status == null && it.start > nowMin }
        nowCard(c, current, next, day)

        // The one suggestion in v1: started late? Offer to move the rest of the day.
        if (current != null && current.status == null) {
            val late = nowMin - current.start
            val key = "$day-${current.block.id}-${current.start}"
            if (late >= 10 && !Prefs.isDismissed(this, key)) lateCard(c, current, late, day, key)
        }

        c.addView(ui.gap(if (ui.isFun) 18 else 12))
        items.forEachIndexed { i, item -> todayRow(c, item, i, day) }
    }

    private fun progress(c: LinearLayout, done: Int, total: Int) {
        val track = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            background = if (ui.isFun) ui.box(Ui.WHITE, 11, Ui.INK, ui.dp(3)) else ui.box(ui.divider, 3)
            val p = if (ui.isFun) ui.dp(3) else 0
            setPadding(p, p, p, p)
        }
        val fill = View(this).apply {
            background = if (ui.isFun) ui.box(Ui.PINK, 8) else ui.box(Ui.TEAL, 3)
        }
        track.addView(fill, ui.lp(0, MATCH, done.toFloat()))
        track.addView(View(this), ui.lp(0, MATCH, (total - done).toFloat()))
        val label = "$done of $total done"
        if (ui.isFun) {
            val r = ui.row()
            r.addView(track, ui.lp(0, ui.dp(22), 1f, r = 10))
            r.addView(ui.text(label, 15f, Ui.INK, true))
            c.addView(r, ui.lp(b = 18))
        } else {
            c.addView(ui.text(label, 13f, ui.sub), ui.lp(b = 6))
            c.addView(track, ui.lp(MATCH, ui.dp(6), b = 16))
        }
    }

    private fun nowCard(c: LinearLayout, current: DayItem?, next: DayItem?, day: Int) {
        val db = Db.get(this)
        val fg = if (ui.isFun) Ui.INK else Ui.WHITE
        val card = if (ui.isFun) ui.card(Ui.BLUE, Ui.PINK) else ui.card(Ui.TEAL)

        if (current == null && next == null) {
            val done = if (ui.isFun) ui.card(Ui.GREEN, Ui.YELLOW) else ui.card(Ui.WHITE)
            done.addView(ui.text("All done for today", 20f, ui.ink, true), ui.lp(b = 4))
            done.addView(ui.text("Rest well. Tomorrow's routine is ready.", 15f, ui.sub))
            c.addView(done, ui.lp())
            return
        }
        val item = current ?: next!!
        val label = if (current != null) "NOW" else "UP NEXT"
        val top = ui.row()
        top.addView(
            if (ui.isFun) ui.chip(label, Ui.WHITE)
            else ui.text(label, 12f, Ui.WHITE, true).apply { letterSpacing = 0.08f }
        )
        top.addView(ui.space())
        top.addView(ui.text("${Time.clock(item.start)} – ${Time.clock(item.end)}", 14f, fg))
        card.addView(top, ui.lp(b = 10))

        countdownTarget = Time.todayAt(if (current != null) item.end else item.start)
        val cd = ui.text(Time.countdown(countdownTarget - System.currentTimeMillis()), if (ui.isFun) 26f else 22f, fg, true)
        countdownView = cd
        val mid = ui.row()
        mid.addView(ui.text(item.block.name, if (ui.isFun) 24f else 22f, fg, true), ui.lp(0, WRAP, 1f, r = 8))
        mid.addView(cd)
        card.addView(mid, ui.lp(b = 2))
        if (current == null) card.addView(ui.text("starts in", 13f, fg), ui.lp(b = 4))

        val buttons = ui.row()
        val openTimer = {
            open(TimerActivity::class.java, item.block.id)
        }
        val first = if (ui.isFun) ui.button(if (current != null) "Open timer" else "Start now", Ui.YELLOW, Ui.INK, Ui.INK, Ui.INK, 46, 16f, openTimer)
        else ui.button(if (current != null) "Open timer" else "Start now", Ui.WHITE, Ui.TEAL, height = 44, size = 15f, onClick = openTimer)
        buttons.addView(first, ui.lp(0, WRAP, 1f))
        if (current != null) {
            val markDone = {
                db.setStatus(day, item.block.id, DONE)
                Alarms.scheduleAll(this)
                render()
            }
            val second = if (ui.isFun) ui.button("Done!", Ui.WHITE, Ui.INK, Ui.INK, Ui.INK, 46, 16f, markDone)
            else ui.button("Done", Ui.TEAL, Ui.WHITE, Ui.WHITE, 0, 44, 15f, markDone)
            buttons.addView(second, ui.lp(0, WRAP, 1f, l = 10))
        }
        card.addView(buttons, ui.lp(t = 10))
        c.addView(card, ui.lp())
    }

    private fun lateCard(c: LinearLayout, item: DayItem, late: Int, day: Int, key: String) {
        val card = if (ui.isFun) ui.card(Ui.ORANGE, Ui.PURPLE, 14) else ui.card(0xFFFFF4E5.toInt(), pad = 14)
        card.addView(
            ui.text("You started $late min late. Move the rest of today $late min later?", 15f, ui.ink),
            ui.lp(b = 10)
        )
        val move = {
            Db.get(this).addShift(day, item.block.startMin, late)
            Alarms.scheduleAll(this)
            render()
        }
        val later = {
            Prefs.dismiss(this, key)
            render()
        }
        val r = ui.row()
        if (ui.isFun) {
            r.addView(ui.button("Move", Ui.WHITE, Ui.INK, Ui.INK, Ui.INK, 44, 15f, move))
            r.addView(ui.button("Not now", Ui.ORANGE, Ui.INK, 0, 0, 44, 15f, later), ui.lp(WRAP, WRAP, l = 8))
        } else {
            r.addView(ui.button("Move", 0xFF9A4A00.toInt(), Ui.WHITE, height = 44, size = 14f, onClick = move))
            r.addView(ui.button("Not now", 0, 0xFF7A3B00.toInt(), height = 44, size = 14f, onClick = later), ui.lp(WRAP, WRAP, l = 8))
        }
        card.addView(r)
        c.addView(card, ui.lp(t = if (ui.isFun) 18 else 12))
    }

    private fun todayRow(c: LinearLayout, item: DayItem, index: Int, day: Int) {
        val db = Db.get(this)
        val done = item.status == DONE
        val toggle = {
            if (done) db.clearStatus(day, item.block.id) else db.setStatus(day, item.block.id, DONE)
            Alarms.scheduleAll(this)
            render()
        }
        val nameText = item.block.name + if (item.status == SKIPPED) "  (skipped)" else ""
        if (ui.isFun) {
            val color = Ui.FUN_COLORS[index % Ui.FUN_COLORS.size]
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                background = ui.box(Ui.WHITE, 12, Ui.INK, ui.dp(3), color, ui.lift)
                setPadding(ui.dp(10), ui.dp(2), ui.dp(2) + ui.lift, ui.dp(2) + ui.lift)
            }
            row.addView(ui.chip(Time.clock(item.start), color), ui.lp(WRAP, WRAP, r = 10))
            row.addView(ui.text(nameText, 16f, Ui.INK, true).apply {
                if (done) paintFlags = paintFlags or android.graphics.Paint.STRIKE_THRU_TEXT_FLAG
            }, ui.lp(0, WRAP, 1f))
            if (item.block.alarm) row.addView(ui.icon(R.drawable.ic_alarm, Ui.INK, 16), ui.lp(ui.dp(16), ui.dp(16), r = 2))
            row.addView(ui.tick(done, toggle))
            c.addView(row, ui.lp(b = 10))
        } else {
            val row = ui.row().apply { minimumHeight = ui.dp(56) }
            row.addView(ui.text(Time.clock(item.start), 13f, ui.sub), ui.lp(ui.dp(48), WRAP))
            row.addView(ui.tick(done, toggle))
            row.addView(ui.text(nameText, 15f, if (item.isClosed) ui.sub else ui.ink), ui.lp(0, WRAP, 1f, l = 4))
            row.addView(ui.text(Time.length(item.block.lengthMin), 12f, ui.sub), ui.lp(WRAP, WRAP, r = 8))
            if (item.block.alarm) row.addView(ui.icon(R.drawable.ic_alarm, ui.sub, 16))
            c.addView(row, ui.lp())
            c.addView(View(this).apply { setBackgroundColor(ui.divider) }, ui.lp(MATCH, 1))
        }
    }

    // ---------- Routine ----------

    private fun routine(c: LinearLayout) {
        header(c, null, "Routine", addButton())
        val blocks = Db.get(this).blocks()
        if (blocks.isEmpty()) {
            c.addView(ui.text("No blocks yet. Tap + to add your first one.", 15f, ui.sub), ui.lp(t = 8))
            return
        }
        blocks.forEachIndexed { i, b ->
            val sub = "${Time.daysText(b.days)} · ${Time.length(b.lengthMin)}"
            val texts = ui.col(
                ui.text(b.name, 16f, ui.ink, true),
                ui.text(sub, 13f, ui.sub)
            )
            val row: LinearLayout
            if (ui.isFun) {
                val color = Ui.FUN_COLORS[i % Ui.FUN_COLORS.size]
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = ui.box(Ui.WHITE, 12, Ui.INK, ui.dp(3), color, ui.lift)
                    setPadding(ui.dp(12), ui.dp(12), ui.dp(12) + ui.lift, ui.dp(12) + ui.lift)
                }
                row.addView(ui.chip(Time.clock(b.startMin), color), ui.lp(WRAP, WRAP, r = 12))
            } else {
                row = ui.row().apply {
                    background = ui.box(Ui.WHITE, 12)
                    setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
                }
                row.addView(ui.text(Time.clock(b.startMin), 15f, Ui.TEAL, true), ui.lp(ui.dp(56), WRAP))
            }
            row.addView(texts, ui.lp(0, WRAP, 1f))
            if (b.alarm) row.addView(ui.icon(R.drawable.ic_alarm, if (ui.isFun) Ui.INK else ui.sub, 18))
            row.isClickable = true
            row.isFocusable = true
            row.contentDescription = "Edit ${b.name}"
            row.background = android.graphics.drawable.RippleDrawable(
                android.content.res.ColorStateList.valueOf(0x22000000), row.background, null
            )
            row.setOnClickListener { open(EditBlockActivity::class.java, b.id) }
            c.addView(row, ui.lp(b = if (ui.isFun) 12 else 8))
        }
    }

    // ---------- Settings ----------

    private fun section(c: LinearLayout, title: String) {
        c.addView(ui.label(title), ui.lp(t = 6, b = 8))
    }

    private fun settings(c: LinearLayout) {
        header(c, null, "Settings")
        val name = Prefs.name(this)

        section(c, if (ui.isFun) "You" else "Profile")
        val profile = ui.card(Ui.WHITE, Ui.GREEN, 14)
        val pr = ui.row()
        val initial = ui.text(name.take(1).uppercase(), 20f, if (ui.isFun) Ui.INK else Ui.WHITE, true).apply {
            gravity = Gravity.CENTER
            background = if (ui.isFun) ui.box(Ui.GREEN, 12, Ui.INK, ui.dp(3)) else ui.box(Ui.TEAL, 24)
        }
        pr.addView(initial, ui.lp(ui.dp(48), ui.dp(48), r = 14))
        pr.addView(ui.text(name, 18f, ui.ink, true), ui.lp(0, WRAP, 1f))
        pr.addView(
            if (ui.isFun) ui.button("Edit", Ui.YELLOW, Ui.INK, Ui.INK, Ui.INK, 44, 15f) { editName() }
            else ui.button("Edit", Ui.WHITE, ui.ink, ui.outline, 0, 44, 14f) { editName() }
        )
        profile.addView(pr)
        c.addView(profile, ui.lp(b = 18))

        section(c, "Theme")
        val themes = ui.row().apply { gravity = Gravity.TOP }
        themes.addView(themeCard(false), ui.lp(0, WRAP, 1f, r = 8))
        themes.addView(themeCard(true), ui.lp(0, WRAP, 1f, l = 8))
        c.addView(themes, ui.lp(b = 18))

        // Only shown when the phone blocks something alarms need.
        notificationsCard(c)
        if (!Alarms.canExact(this) && Build.VERSION.SDK_INT >= 31) {
            fixCard(c, "Allow exact alarms so blocks start right on time.") {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            }
        }
        if (!Alarms.canFullScreen(this) && Build.VERSION.SDK_INT >= 34) {
            fixCard(c, "Allow full-screen alarms so they show over the lock screen.") {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            }
        }

        val about = ui.card(Ui.WHITE, Ui.BLUE, 14)
        val ar = ui.row()
        ar.addView(ui.text("Dinlipi", 16f, ui.ink, ui.isFun), ui.lp(0, WRAP, 1f))
        ar.addView(ui.text("Version 1.0 · free", 15f, if (ui.isFun) Ui.INK else ui.sub))
        about.addView(ar)
        c.addView(about, ui.lp(t = 6))
    }

    /** Without notifications, reminders and alarms are dropped silently, so say so. */
    private fun notificationsCard(c: LinearLayout) {
        if (Alarms.notificationsOn(this)) return
        fixCard(c, "Notifications are off, so reminders and alarms can't ring.") {
            startActivity(
                if (Build.VERSION.SDK_INT >= 26) Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                else Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$packageName"))
            )
        }
    }

    private fun fixCard(c: LinearLayout, message: String, onClick: () -> Unit) {
        val card = if (ui.isFun) ui.card(Ui.ORANGE, Ui.PURPLE, 14) else ui.card(0xFFFFF4E5.toInt(), pad = 14)
        card.addView(ui.text(message, 15f, ui.ink), ui.lp(b = 10))
        card.addView(ui.secondary("Allow", 44, onClick), ui.lp(WRAP, WRAP))
        c.addView(card, ui.lp(b = 18))
    }

    private fun themeCard(fun_: Boolean): View {
        val selected = fun_ == ui.isFun
        val card = ui.col().apply {
            val pad = ui.dp(12)
            if (ui.isFun) {
                background = if (selected) ui.box(Ui.PINK, 16, Ui.INK, ui.dp(3), Ui.INK, ui.lift)
                else ui.box(Ui.WHITE, 16, Ui.INK, ui.dp(3), 0, ui.lift)
                setPadding(pad, pad, pad + ui.lift, pad + ui.lift)
            } else {
                background = if (selected) ui.box(Ui.WHITE, 14, Ui.TEAL, ui.dp(2.5f)) else ui.box(Ui.WHITE, 14, ui.divider, ui.dp(1.5f))
                setPadding(pad, pad, pad, pad)
            }
            isClickable = true
            isFocusable = true
            contentDescription = (if (fun_) "Fun theme" else "Calm theme") + if (selected) ", selected" else ""
            setOnClickListener {
                Prefs.setFun(this@MainActivity, fun_)
                render()
            }
        }
        card.addView(if (fun_) funPreview() else calmPreview(), ui.lp(MATCH, ui.dp(96), b = 10))
        val lr = ui.row()
        lr.addView(ui.text(if (fun_) "Fun" else "Calm", 16f, ui.ink, true), ui.lp(0, WRAP, 1f))
        if (selected) lr.addView(ui.icon(R.drawable.ic_check, if (ui.isFun) Ui.INK else Ui.TEAL, 20))
        card.addView(lr)
        return card
    }

    private fun calmPreview(): View {
        val p = ui.col().apply {
            background = ui.box(0xFFF6F7F9.toInt(), 8)
            setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10))
        }
        p.addView(View(this).apply { background = ui.box(Ui.TEAL, 6) }, ui.lp(MATCH, ui.dp(28), b = 6))
        for (r in intArrayOf(16, 36, 26)) {
            p.addView(View(this).apply { background = ui.box(ui.divider, 3) }, ui.lp(MATCH, ui.dp(10), r = r, b = 5))
        }
        return p
    }

    private fun funPreview(): View {
        val p = ui.col().apply {
            background = ui.box(Ui.CREAM, 8, Ui.INK, ui.dp(2))
            setPadding(ui.dp(8), ui.dp(8), ui.dp(8), ui.dp(8))
        }
        val o = ui.dp(3)
        p.addView(View(this).apply { background = ui.box(Ui.BLUE, 6, Ui.INK, ui.dp(2), Ui.YELLOW, o) }, ui.lp(MATCH, ui.dp(28), b = 6))
        val r = ui.row()
        r.addView(View(this).apply { background = ui.box(Ui.YELLOW, 4, Ui.INK, ui.dp(2), Ui.PURPLE, o) }, ui.lp(0, ui.dp(18), 1f, r = 4))
        r.addView(View(this).apply { background = ui.box(Ui.GREEN, 4, Ui.INK, ui.dp(2), Ui.ORANGE, o) }, ui.lp(0, ui.dp(18), 1f, l = 4))
        p.addView(r, ui.lp(b = 6))
        p.addView(View(this).apply { background = ui.box(Ui.PURPLE, 4, Ui.INK, ui.dp(2)) }, ui.lp(MATCH, ui.dp(14), r = 30))
        return p
    }

    private fun editName() {
        val input = ui.input(Prefs.name(this), "Your name", shadow = Ui.PURPLE)
        val box = FrameLayout(this).apply {
            setPadding(ui.dp(20), ui.dp(8), ui.dp(20), 0)
            addView(input)
        }
        AlertDialog.Builder(this)
            .setTitle("Your name")
            .setView(box)
            .setPositiveButton("Save") { _, _ ->
                val v = input.text.toString().trim()
                if (v.isNotEmpty()) Prefs.setName(this, v)
                render()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
