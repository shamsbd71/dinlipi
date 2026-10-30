package com.dinlipi.app

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.Toast

/** First launch: ask only for the person's name. */
class OnboardingActivity : Activity() {

    private lateinit var ui: Ui
    private lateinit var nameInput: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this)

        val content = ui.col().apply { setPadding(ui.dp(24), 0, ui.dp(24), ui.dp(28)) }

        content.addView(ui.space(1f))

        val logo = ui.icon(R.drawable.ic_today, if (ui.isFun) Ui.INK else Ui.WHITE, 34).apply {
            scaleType = android.widget.ImageView.ScaleType.CENTER
            background = if (ui.isFun) ui.box(Ui.YELLOW, 14, Ui.INK, ui.dp(3), Ui.INK, ui.lift) else ui.box(Ui.TEAL, 18)
            setPadding(0, 0, ui.lift, ui.lift)
        }
        content.addView(logo, ui.lp(ui.dp(64) + ui.lift, ui.dp(64) + ui.lift))

        content.addView(
            ui.text(if (ui.isFun) "Hey there! Welcome to Dinlipi" else "Welcome to Dinlipi", if (ui.isFun) 32f else 30f, ui.ink, true),
            ui.lp(t = 24)
        )
        content.addView(
            ui.text(
                if (ui.isFun) "Let's make your day fun, one block at a time. What's your name?"
                else "Plan your day one block at a time. First, what should we call you?",
                16f, ui.sub
            ),
            ui.lp(t = 8)
        )

        content.addView(ui.label("Your name"), ui.lp(t = 28, b = 8))
        nameInput = ui.input("", "Type your name", shadow = Ui.PURPLE).apply {
            imeOptions = EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { _, action, _ ->
                if (action == EditorInfo.IME_ACTION_DONE) { finishSetup(); true } else false
            }
        }
        content.addView(nameInput, ui.lp())

        content.addView(ui.space(1.3f))
        content.addView(ui.primary(if (ui.isFun) "Let's start!" else "Continue") { finishSetup() }, ui.lp())

        ui.setContent(content)
    }

    private fun finishSetup() {
        val name = nameInput.text.toString().trim()
        if (name.isEmpty()) {
            Toast.makeText(this, "Please type your name", Toast.LENGTH_SHORT).show()
            return
        }
        Prefs.setName(this, name)
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
