package com.dinlipi.app

import android.app.Activity
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.InsetDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.RippleDrawable
import android.os.Build
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

/**
 * The two themes, drawn in code with plain shapes (no images, no animations).
 * Calm: flat, soft grey, one teal accent. Fun: cream, bold black borders, solid coloured shadows.
 */
class Ui(val a: Activity) {
    companion object {
        const val WHITE = 0xFFFFFFFF.toInt()
        const val TEAL = 0xFF0F766E.toInt()
        const val TEAL_DARK = 0xFF0B3D39.toInt()
        const val INK = 0xFF161616.toInt()
        const val CREAM = 0xFFFFF4D6.toInt()
        const val YELLOW = 0xFFFFD23F.toInt()
        const val PINK = 0xFFFF6FAE.toInt()
        const val BLUE = 0xFF6FB8FF.toInt()
        const val GREEN = 0xFF5FD99A.toInt()
        const val ORANGE = 0xFFFFA552.toInt()
        const val PURPLE = 0xFFB79CFF.toInt()
        val FUN_COLORS = intArrayOf(GREEN, YELLOW, PURPLE, PINK, BLUE, ORANGE)
    }

    val isFun = Prefs.isFun(a)
    private val density = a.resources.displayMetrics.density

    fun dp(v: Float): Int = (v * density + 0.5f).toInt()
    fun dp(v: Int): Int = dp(v.toFloat())

    val bg = if (isFun) CREAM else 0xFFF6F7F9.toInt()
    val ink = if (isFun) INK else 0xFF16181D.toInt()
    val sub = if (isFun) 0xFF444444.toInt() else 0xFF5B616E.toInt()
    val divider = 0xFFE3E6EB.toInt()
    val outline = 0xFFC9CED6.toInt()
    val accent = if (isFun) PINK else TEAL
    val boldFace: Typeface = Typeface.DEFAULT_BOLD

    /** Fun mode lifts boxes off a solid shadow; Calm has none. */
    val lift = if (isFun) dp(4) else 0
    private val funLine = dp(3)

    // ---------- drawables ----------

    fun box(fill: Int, radius: Int = 14, line: Int = 0, lineW: Int = 0, shadow: Int = 0, off: Int = 0): Drawable {
        val r = dp(radius).toFloat()
        val main = GradientDrawable().apply {
            setColor(fill)
            cornerRadius = r
            if (lineW > 0) setStroke(lineW, line)
        }
        if (off == 0) return main
        val sh = GradientDrawable().apply {
            setColor(shadow)
            cornerRadius = r
        }
        return LayerDrawable(arrayOf(sh, main)).apply {
            setLayerInset(0, off, off, 0, 0)
            setLayerInset(1, 0, 0, off, off)
        }
    }

    private fun ripple(d: Drawable): Drawable = RippleDrawable(ColorStateList.valueOf(0x22000000), d, null)

    /** A card: Calm = white flat box, Fun = bordered box on a coloured shadow. */
    fun card(fill: Int = WHITE, shadow: Int = INK, pad: Int = 16): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        background = if (isFun) box(fill, 16, INK, funLine, shadow, dp(5)) else box(fill, 16)
        val o = if (isFun) dp(5) else 0
        setPadding(dp(pad), dp(pad), dp(pad) + o, dp(pad) + o)
    }

    // ---------- views ----------

    fun text(s: CharSequence, size: Float = 15f, color: Int = ink, bold: Boolean = false): TextView =
        TextView(a).apply {
            text = s
            textSize = size
            setTextColor(color)
            if (bold) typeface = boldFace
        }

    fun chip(s: String, fill: Int): TextView = text(s, 13f, INK, true).apply {
        background = box(fill, 8, INK, dp(2.5f))
        setPadding(dp(9), dp(2), dp(9), dp(2))
    }

    fun button(
        label: String, fill: Int, textColor: Int, line: Int = 0, shadow: Int = 0,
        height: Int = 48, size: Float = 16f, onClick: () -> Unit,
    ): Button {
        val off = lift
        val lw = if (line == 0) 0 else if (isFun) funLine else dp(1.5f)
        return Button(a).apply {
            text = label
            isAllCaps = false
            textSize = size
            setTextColor(textColor)
            typeface = boldFace
            gravity = Gravity.CENTER
            minWidth = 0
            minimumWidth = 0
            minHeight = dp(height) + off
            minimumHeight = dp(height) + off
            stateListAnimator = null
            setPadding(dp(16), 0, dp(16) + off, off)
            background = ripple(box(fill, if (isFun) 12 else 10, line, lw, shadow, off))
            setOnClickListener { onClick() }
        }
    }

    fun primary(label: String, height: Int = 52, onClick: () -> Unit): Button =
        if (isFun) button(label, GREEN, INK, INK, INK, height, 18f, onClick)
        else button(label, TEAL, WHITE, height = height, onClick = onClick)

    fun secondary(label: String, height: Int = 52, onClick: () -> Unit): Button =
        if (isFun) button(label, WHITE, INK, INK, INK, height, 17f, onClick)
        else button(label, WHITE, ink, outline, height = height, onClick = onClick)

    /** A tappable field that looks like an input (used for the time picker). */
    fun field(label: String, shadow: Int, onClick: () -> Unit): Button {
        val b = if (isFun) button(label, WHITE, INK, INK, shadow, 52, 18f, onClick)
        else button(label, WHITE, ink, outline, 0, 52, 17f, onClick)
        b.gravity = Gravity.START or Gravity.CENTER_VERTICAL
        b.typeface = Typeface.DEFAULT
        return b
    }

    fun input(value: String, hint: String, numeric: Boolean = false, shadow: Int = BLUE): EditText =
        EditText(a).apply {
            setText(value)
            this.hint = hint
            textSize = 17f
            setTextColor(ink)
            setHintTextColor(sub)
            maxLines = 1
            inputType = if (numeric) InputType.TYPE_CLASS_NUMBER
            else InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_CAP_WORDS
            background = if (isFun) box(WHITE, 12, INK, funLine, shadow, lift) else box(WHITE, 10, outline, dp(1.5f))
            setPadding(dp(14), 0, dp(14) + lift, lift)
            minHeight = dp(52) + lift
            gravity = Gravity.CENTER_VERTICAL
        }

    fun label(s: String): TextView =
        if (isFun) text(s, 15f, INK, true) else text(s, 13f, sub, true)

    fun icon(res: Int, color: Int, size: Int = 22): ImageView = ImageView(a).apply {
        setImageResource(res)
        setColorFilter(color)
        layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
    }

    /** Square (Fun) or round (Calm) icon button, 46dp. */
    fun iconButton(res: Int, desc: String, fill: Int, tint: Int, onClick: () -> Unit): ImageButton =
        ImageButton(a).apply {
            setImageResource(res)
            setColorFilter(tint)
            contentDescription = desc
            scaleType = ImageView.ScaleType.CENTER
            background = ripple(
                if (isFun) box(fill, 12, INK, funLine, INK, lift) else box(fill, 23)
            )
            setPadding(0, 0, lift, lift)
            layoutParams = LinearLayout.LayoutParams(dp(46) + lift, dp(46) + lift)
            setOnClickListener { onClick() }
        }

    /** Tick box for a list row: 44dp touch area around a small box or circle. */
    fun tick(done: Boolean, onClick: () -> Unit): ImageButton = ImageButton(a).apply {
        contentDescription = if (done) "Mark not done" else "Mark done"
        scaleType = ImageView.ScaleType.CENTER
        if (isFun) {
            background = InsetDrawable(box(if (done) GREEN else WHITE, 7, INK, dp(2.5f)), dp(9))
            if (done) setImageResource(R.drawable.ic_check)
            setColorFilter(INK)
        } else {
            background = null
            setImageResource(if (done) R.drawable.ic_check else R.drawable.ic_circle)
            setColorFilter(if (done) TEAL else 0xFFA3A9B3.toInt())
        }
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(44))
        setOnClickListener { onClick() }
    }

    // ---------- layout ----------

    fun col(vararg kids: View): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.VERTICAL
        kids.forEach { addView(it) }
    }

    fun row(vararg kids: View): LinearLayout = LinearLayout(a).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        kids.forEach { addView(it) }
    }

    fun lp(w: Int = MATCH, h: Int = WRAP, weight: Float = 0f, l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0) =
        LinearLayout.LayoutParams(w, h, weight).apply { setMargins(dp(l), dp(t), dp(r), dp(b)) }

    fun space(weight: Float = 1f): View = View(a).apply { layoutParams = LinearLayout.LayoutParams(0, 0, weight) }

    fun gap(h: Int): View = View(a).apply { layoutParams = LinearLayout.LayoutParams(1, dp(h)) }

    /** Sets the screen, its background and system-bar colours, and keeps content clear of the bars. */
    @Suppress("DEPRECATION")
    fun setContent(root: View, background: Int = bg, lightBars: Boolean = true) {
        val w = a.window
        root.setBackgroundColor(background)
        w.setBackgroundDrawable(ColorDrawable(background))
        a.setContentView(root)
        if (Build.VERSION.SDK_INT >= 30) {
            w.setDecorFitsSystemWindows(false)
            w.statusBarColor = Color.TRANSPARENT
            w.navigationBarColor = Color.TRANSPARENT
            w.isNavigationBarContrastEnforced = false
            val flags = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            w.insetsController?.setSystemBarsAppearance(if (lightBars) flags else 0, flags)
        } else {
            w.statusBarColor = if (lightBars && Build.VERSION.SDK_INT < 23) 0xFF444444.toInt() else background
            if (Build.VERSION.SDK_INT >= 23) {
                w.decorView.systemUiVisibility = if (lightBars) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
            }
        }
        // Keep the screen's own padding and add the system bars on top of it.
        val pl = root.paddingLeft; val pt = root.paddingTop; val pr = root.paddingRight; val pb = root.paddingBottom
        root.setOnApplyWindowInsetsListener { v, ins ->
            if (Build.VERSION.SDK_INT >= 30) {
                val b = ins.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(pl + b.left, pt + b.top, pr + b.right, pb + b.bottom)
            } else {
                v.setPadding(
                    pl + ins.systemWindowInsetLeft, pt + ins.systemWindowInsetTop,
                    pr + ins.systemWindowInsetRight, pb + ins.systemWindowInsetBottom
                )
            }
            ins
        }
        root.requestApplyInsets()
    }
}

/** Countdown progress: a ring in Calm, a chunky bar in Fun. [fraction] = time left (1 = full). */
class TimerProgress(c: Context, private val isFun: Boolean) : View(c) {
    var fraction = 1f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }

    private val d = c.resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (!isFun) {
            val sw = 14 * d
            p.style = Paint.Style.STROKE
            p.strokeWidth = sw
            p.strokeCap = Paint.Cap.ROUND
            r.set(sw / 2, sw / 2, w - sw / 2, h - sw / 2)
            p.color = 0xFFE3E6EB.toInt()
            canvas.drawArc(r, 0f, 360f, false, p)
            p.color = Ui.TEAL
            if (fraction > 0f) canvas.drawArc(r, -90f, 360f * fraction, false, p)
        } else {
            val bw = 3 * d
            val rad = h / 2
            p.style = Paint.Style.FILL
            p.color = Ui.WHITE
            r.set(bw / 2, bw / 2, w - bw / 2, h - bw / 2)
            canvas.drawRoundRect(r, rad, rad, p)
            if (fraction > 0f) {
                p.color = Ui.YELLOW
                r.set(bw, bw, bw + (w - 2 * bw) * fraction, h - bw)
                canvas.drawRoundRect(r, rad - bw, rad - bw, p)
            }
            p.style = Paint.Style.STROKE
            p.strokeWidth = bw
            p.color = Ui.INK
            r.set(bw / 2, bw / 2, w - bw / 2, h - bw / 2)
            canvas.drawRoundRect(r, rad, rad, p)
        }
    }
}
