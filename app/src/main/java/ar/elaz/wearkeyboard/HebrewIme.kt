package ar.elaz.wearkeyboard

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Stage 0 Hebrew keyboard for round Wear OS screens.
 *
 * Layout: the standard Israeli layout without the final letters (22 keys, 3 rows).
 * Final forms (ך ם ן ף ץ) appear automatically when a word ends,
 * or by long-pressing כ מ נ פ צ.
 */
class HebrewIme : InputMethodService() {

    private enum class Layer { HEBREW, SYMBOLS }

    private var layer = Layer.HEBREW
    private var root: LinearLayout? = null
    private var preview: TextView? = null
    private val handler = Handler(Looper.getMainLooper())

    private val repeatDelete = object : Runnable {
        override fun run() {
            deleteOne()
            handler.postDelayed(this, REPEAT_MS)
        }
    }

    // We draw our own preview line, so the keyboard can use the whole watch face.
    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onCreateInputView(): View {
        val r = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setBackgroundColor(Color.BLACK)
        }
        root = r
        render()
        return r
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        val cls = (info?.inputType ?: 0) and InputType.TYPE_MASK_CLASS
        layer = when (cls) {
            InputType.TYPE_CLASS_NUMBER,
            InputType.TYPE_CLASS_PHONE,
            InputType.TYPE_CLASS_DATETIME -> Layer.SYMBOLS
            else -> Layer.HEBREW
        }
        render()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        handler.removeCallbacks(repeatDelete)
        super.onFinishInputView(finishingInput)
    }

    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        updatePreview()
    }

    // ---------------------------------------------------------------- layout

    private fun render() {
        val r = root ?: return
        r.removeAllViews()

        val dm = resources.displayMetrics
        val w = dm.widthPixels
        val h = dm.heightPixels
        val round = resources.configuration.isScreenRound

        var y = (h * TOP_PAD).toInt()
        r.setPadding(0, y, 0, 0)

        val previewH = (h * PREVIEW_H).toInt()
        val p = TextView(this).apply {
            gravity = Gravity.CENTER
            textDirection = View.TEXT_DIRECTION_RTL
            includeFontPadding = false
            maxLines = 1
            setTextColor(0xFFDDDDDD.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            layoutParams = LinearLayout.LayoutParams(chord(w, h, y, y + previewH, round), previewH).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
        }
        preview = p
        r.addView(p)
        y += previewH

        val rowH = (h * ROW_H).toInt()
        val rows = if (layer == Layer.HEBREW) HEBREW_ROWS else SYMBOL_ROWS
        for (keys in rows) {
            val row = makeRow(chord(w, h, y, y + rowH, round), rowH)
            for (c in keys) row.addView(charKey(c))
            r.addView(row)
            y += rowH
        }

        val bottom = makeRow(chord(w, h, y, y + rowH, round), rowH)
        bottom.addView(actionKey(if (layer == Layer.HEBREW) "123" else "אבג", 1.3f, 12f) {
            layer = if (layer == Layer.HEBREW) Layer.SYMBOLS else Layer.HEBREW
            render()
        })
        bottom.addView(actionKey("🌐", 1f, 13f) { switchKeyboard() })
        bottom.addView(actionKey("רווח", 2.2f, 11f) { commit(" ") })
        bottom.addView(backspaceKey())
        bottom.addView(actionKey("↵", 1.3f, 16f) { enter() })
        r.addView(bottom)
        y += rowH

        // Fill the rest so the keyboard covers the whole screen.
        r.addView(View(this).apply {
            layoutParams = LinearLayout.LayoutParams(1, max(0, h - y))
        })

        updatePreview()
    }

    /** Usable width of the horizontal band [top, bottom] on a round screen. */
    private fun chord(w: Int, h: Int, top: Int, bottom: Int, round: Boolean): Int {
        if (!round) return (w * 0.98f).toInt()
        val radius = min(w, h) / 2f
        val cy = h / 2f
        val d = max(abs(top - cy), abs(bottom - cy))
        if (d >= radius) return 0
        return (2f * sqrt(radius * radius - d * d) * 0.97f).toInt()
    }

    private fun makeRow(width: Int, height: Int) = LinearLayout(this).apply {
        orientation = LinearLayout.HORIZONTAL
        // Physical keyboard order (ק on the left), whatever the locale direction.
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        layoutParams = LinearLayout.LayoutParams(width, height).apply {
            gravity = Gravity.CENTER_HORIZONTAL
        }
    }

    private fun baseKey(label: String, weight: Float, sp: Float, special: Boolean) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        includeFontPadding = false
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = Typeface.DEFAULT_BOLD
        background = keyBackground(special)
        isClickable = true
        val m = dp(1.5f).toInt()
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weight).apply {
            setMargins(m, m, m, m)
        }
    }

    private fun charKey(c: Char): TextView {
        val s = c.toString()
        val key = baseKey(s, 1f, 17f, false)
        key.setOnClickListener { v -> tap(v); commit(s) }
        val fin = FINALS[c]
        if (fin != null) {
            key.setOnLongClickListener { v -> tap(v); commitRaw(fin.toString()); true }
        }
        return key
    }

    private fun actionKey(label: String, weight: Float, sp: Float, onTap: () -> Unit): TextView =
        baseKey(label, weight, sp, true).apply {
            setOnClickListener { v -> tap(v); onTap() }
        }

    private fun backspaceKey(): TextView = baseKey("⌫", 1.3f, 15f, true).apply {
        setOnTouchListener { v, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    v.isPressed = true
                    tap(v)
                    deleteOne()
                    handler.postDelayed(repeatDelete, REPEAT_START_MS)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.isPressed = false
                    handler.removeCallbacks(repeatDelete)
                }
            }
            true
        }
    }

    private fun keyBackground(special: Boolean): StateListDrawable {
        val normal = if (special) 0xFF1F3A5F.toInt() else 0xFF2B2B2B.toInt()
        val pressed = if (special) 0xFF3D6FA3.toInt() else 0xFF616161.toInt()
        fun shape(color: Int) = GradientDrawable().apply {
            cornerRadius = dp(8f)
            setColor(color)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), shape(pressed))
            addState(intArrayOf(), shape(normal))
        }
    }

    private fun dp(v: Float) = v * resources.displayMetrics.density

    private fun tap(v: View) {
        v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
    }

    // ---------------------------------------------------------------- text

    /** Types [s]. A word-ending character first turns the last letter into its final form. */
    private fun commit(s: String) {
        if (s.length == 1 && s[0] in WORD_END && !isPassword()) applyFinalForm()
        commitRaw(s)
    }

    private fun commitRaw(s: String) {
        currentInputConnection?.commitText(s, 1)
        updatePreview()
    }

    private fun applyFinalForm() {
        val ic = currentInputConnection ?: return
        val before = ic.getTextBeforeCursor(2, 0) ?: return
        if (before.length < 2) return
        val fin = FINALS[before[before.length - 1]] ?: return
        if (!isHebrewLetter(before[before.length - 2])) return
        ic.beginBatchEdit()
        ic.deleteSurroundingText(1, 0)
        ic.commitText(fin.toString(), 1)
        ic.endBatchEdit()
    }

    private fun deleteOne() {
        val ic = currentInputConnection ?: return
        val selected = ic.getSelectedText(0)
        when {
            !selected.isNullOrEmpty() -> ic.commitText("", 1)
            ic.getTextBeforeCursor(1, 0).isNullOrEmpty() -> sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            else -> ic.deleteSurroundingTextInCodePoints(1, 0)
        }
        updatePreview()
    }

    private fun enter() {
        if (!isPassword()) applyFinalForm()
        // Wear OS fields are single-line: Enter runs the field's action (send / done).
        if (!sendDefaultEditorAction(true)) sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        updatePreview()
    }

    private fun switchKeyboard() {
        if (!switchToNextInputMethod(false)) {
            getSystemService(InputMethodManager::class.java)?.showInputMethodPicker()
        }
    }

    private fun updatePreview() {
        val p = preview ?: return
        val text = currentInputConnection?.getTextBeforeCursor(PREVIEW_CHARS * 2, 0)?.toString().orEmpty()
        val shown = when {
            text.isEmpty() -> null
            isPassword() -> "•".repeat(min(text.length, PREVIEW_CHARS))
            text.length > PREVIEW_CHARS -> "…" + text.takeLast(PREVIEW_CHARS)
            else -> text
        }
        p.text = shown ?: "הקלד…"
        p.alpha = if (shown == null) 0.45f else 1f
    }

    private fun isPassword(): Boolean {
        val t = currentInputEditorInfo?.inputType ?: return false
        val cls = t and InputType.TYPE_MASK_CLASS
        val v = t and InputType.TYPE_MASK_VARIATION
        return when (cls) {
            InputType.TYPE_CLASS_TEXT ->
                v == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    v == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD ||
                    v == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
            InputType.TYPE_CLASS_NUMBER -> v == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            else -> false
        }
    }

    private companion object {
        // Vertical layout, as fractions of screen height.
        const val TOP_PAD = 0.06f
        const val PREVIEW_H = 0.14f
        const val ROW_H = 0.17f

        const val PREVIEW_CHARS = 14
        const val REPEAT_START_MS = 450L
        const val REPEAT_MS = 70L

        val HEBREW_ROWS = listOf("קראטופ", "שדגכעיחל", "זסבהנמצת")
        val SYMBOL_ROWS = listOf("123456", "7890-@()", ".,?!'\":/")

        val FINALS = mapOf('כ' to 'ך', 'מ' to 'ם', 'נ' to 'ן', 'פ' to 'ף', 'צ' to 'ץ')

        /** Characters that end a word. Not ' or " — those appear inside words (צ'יפס, צה"ל). */
        val WORD_END = setOf(' ', '.', ',', '?', '!', ':', ';', ')', '/')

        fun isHebrewLetter(c: Char) = c in 'א'..'ת'
    }
}
