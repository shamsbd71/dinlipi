package com.dinlipi.app

import android.app.Activity
import android.app.AlertDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Add or edit a block: name, start, length, repeat days, reminder or alarm. Nothing else. */
class EditBlockActivity : Activity() {

    private lateinit var ui: Ui
    private var id = -1L
    private var start = 7 * 60
    private var days = 127
    private var alarm = false

    private lateinit var nameInput: EditText
    private lateinit var lengthInput: EditText
    private lateinit var startField: Button
    private val dayButtons = ArrayList<Button>()
    private lateinit var reminderBtn: Button
    private lateinit var alarmBtn: Button
    private lateinit var segment: LinearLayout

    private val dayLetters = arrayOf("S", "M", "T", "W", "T", "F", "S")
    private val dayNames = arrayOf("Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)
        id = intent.getLongExtra(Alarms.EXTRA_ID, -1)
        val existing = if (id > 0) Db.get(this).block(id) else null
        var name = ""
        var length = 30
        if (existing != null) {
            name = existing.name
            start = existing.startMin
            length = existing.lengthMin
            days = existing.days
            alarm = existing.alarm
        } else {
            id = -1
            start = ((Time.minuteOfDay() / 60) + 1) % 24 * 60
        }

        val root = ui.col()
        val top = ui.row().apply { setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(4)) }
        top.addView(
            ui.iconButton(R.drawable.ic_close, "Close", if (ui.isFun) Ui.WHITE else ui.bg, ui.ink) { finish() }
        )
        top.addView(ui.text(if (existing == null) "New block" else "Edit block", if (ui.isFun) 26f else 20f, ui.ink, true), ui.lp(WRAP, WRAP, l = 10))
        root.addView(top, ui.lp())

        val c = ui.col().apply { setPadding(ui.dp(20), ui.dp(12), ui.dp(20), ui.dp(20)) }

        c.addView(ui.label("Name"), ui.lp(b = 8))
        nameInput = ui.input(name, "e.g. Exercise", shadow = Ui.BLUE)
        c.addView(nameInput, ui.lp(b = 22))

        val times = ui.row().apply { gravity = android.view.Gravity.TOP }
        val startCol = ui.col(ui.label("Starts at"))
        startField = ui.field(Time.clock(start), Ui.YELLOW) { pickTime() }
        startCol.addView(startField, ui.lp(t = 8))
        val lenCol = ui.col(ui.label("Lasts (min)"))
        lengthInput = ui.input(length.toString(), "30", numeric = true, shadow = Ui.GREEN)
        lenCol.addView(lengthInput, ui.lp(t = 8))
        times.addView(startCol, ui.lp(0, WRAP, 1f, r = 8))
        times.addView(lenCol, ui.lp(0, WRAP, 1f, l = 8))
        c.addView(times, ui.lp(b = 22))

        c.addView(ui.label("Repeat on"), ui.lp(b = 8))
        val dayRow = ui.row()
        for (i in 0 until 7) {
            val b = ui.button(dayLetters[i], Ui.WHITE, ui.ink, height = 44, size = 15f) {
                days = days xor (1 shl i)
                styleDays()
            }
            b.contentDescription = dayNames[i]
            b.setPadding(0, 0, ui.lift, ui.lift)
            dayButtons += b
            dayRow.addView(b, ui.lp(0, WRAP, 1f, l = if (i == 0) 0 else 4))
        }
        c.addView(dayRow, ui.lp(b = 22))

        c.addView(ui.label("When it starts"), ui.lp(b = 8))
        segment = ui.row()
        reminderBtn = ui.button("Reminder", Ui.WHITE, ui.ink, height = 44, size = 15f) { alarm = false; styleSegment() }
        alarmBtn = ui.button("Alarm", Ui.WHITE, ui.ink, height = 44, size = 15f) { alarm = true; styleSegment() }
        segment.addView(reminderBtn, ui.lp(0, WRAP, 1f))
        segment.addView(alarmBtn, ui.lp(0, WRAP, 1f, l = if (ui.isFun) 14 else 0))
        c.addView(segment, ui.lp(b = 8))
        c.addView(
            ui.text("Reminder: a notification. Alarm: rings full screen until you respond.", 13f, ui.sub),
            ui.lp(b = 24)
        )

        val scroll = ScrollView(this).apply {
            isFillViewport = true
            addView(c)
        }
        root.addView(scroll, ui.lp(MATCH, 0, 1f))

        val bottom = ui.col().apply { setPadding(ui.dp(20), ui.dp(8), ui.dp(20), ui.dp(20)) }
        val save = if (ui.isFun) ui.button("Save", Ui.PINK, Ui.INK, Ui.INK, Ui.INK, 56, 19f) { save() }
        else ui.primary("Save") { save() }
        bottom.addView(save, ui.lp())
        if (existing != null) {
            bottom.addView(
                ui.button("Delete block", 0, 0xFFB42318.toInt(), height = 44, size = 15f) { confirmDelete() },
                ui.lp(t = 8)
            )
        }
        root.addView(bottom, ui.lp())

        styleDays()
        styleSegment()
        ui.setContent(root)
    }

    private fun styleDays() {
        dayButtons.forEachIndexed { i, b ->
            val on = days and (1 shl i) != 0
            b.isSelected = on
            b.background = when {
                ui.isFun && on -> ui.box(Ui.YELLOW, 10, Ui.INK, ui.dp(3), Ui.INK, ui.lift)
                ui.isFun -> ui.box(Ui.WHITE, 10, Ui.INK, ui.dp(3), 0, ui.lift)
                on -> ui.box(Ui.TEAL, 22, Ui.TEAL, ui.dp(1.5f))
                else -> ui.box(Ui.WHITE, 22, ui.outline, ui.dp(1.5f))
            }
            b.setTextColor(if (on && !ui.isFun) Ui.WHITE else ui.ink)
        }
    }

    private fun styleSegment() {
        if (ui.isFun) {
            reminderBtn.background = ui.box(if (!alarm) Ui.PURPLE else Ui.WHITE, 12, Ui.INK, ui.dp(3), if (!alarm) Ui.INK else 0, ui.lift)
            alarmBtn.background = ui.box(if (alarm) Ui.PURPLE else Ui.WHITE, 12, Ui.INK, ui.dp(3), if (alarm) Ui.INK else 0, ui.lift)
        } else {
            segment.background = ui.box(ui.divider, 12)
            segment.setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4))
            reminderBtn.background = ui.box(if (!alarm) Ui.WHITE else 0, 9)
            alarmBtn.background = ui.box(if (alarm) Ui.WHITE else 0, 9)
            reminderBtn.setTextColor(if (!alarm) ui.ink else ui.sub)
            alarmBtn.setTextColor(if (alarm) ui.ink else ui.sub)
        }
    }

    private fun pickTime() {
        TimePickerDialog(this, { _, h, m ->
            start = h * 60 + m
            startField.text = Time.clock(start)
        }, start / 60, start % 60, true).show()
    }

    private fun save() {
        val name = nameInput.text.toString().trim()
        val length = lengthInput.text.toString().toIntOrNull()
        when {
            name.isEmpty() -> toast("Please give the block a name")
            length == null || length < 1 || length > 1440 -> toast("Length must be 1 to 1440 minutes")
            days == 0 -> toast("Pick at least one day")
            else -> {
                Db.get(this).save(Block(id, name, start, length, days, alarm))
                Alarms.scheduleAll(this)
                finish()
            }
        }
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle("Delete this block?")
            .setMessage("It will be removed from every day.")
            .setPositiveButton("Delete") { _, _ ->
                Db.get(this).delete(id)
                Alarms.cancel(this, id)
                finish()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
}
