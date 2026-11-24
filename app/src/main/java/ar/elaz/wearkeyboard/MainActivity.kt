package ar.elaz.wearkeyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Opens from the watch's app list: shows whether the keyboard is active, plus a field to test it. */
class MainActivity : Activity() {

    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = (resources.displayMetrics.widthPixels * 0.14f).toInt()

        status = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 13f
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(pad, pad, pad, pad * 2)
            addView(TextView(this@MainActivity).apply {
                text = getString(R.string.app_name)
                gravity = Gravity.CENTER
                textSize = 18f
                setTypeface(typeface, Typeface.BOLD)
            })
            addView(status)
            addView(EditText(this@MainActivity).apply {
                hint = "נסו להקליד כאן"
                textDirection = View.TEXT_DIRECTION_RTL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                )
            })
            addView(Button(this@MainActivity).apply {
                text = "הגדרות מקלדת"
                setOnClickListener { openKeyboardSettings() }
            })
        }

        setContentView(ScrollView(this).apply {
            isFillViewport = true
            addView(column)
        })
    }

    override fun onResume() {
        super.onResume()
        status.text = statusText()
    }

    private fun statusText(): String {
        val imm = getSystemService(InputMethodManager::class.java)
        val enabled = imm?.enabledInputMethodList?.any { it.packageName == packageName } == true
        val current = try {
            Settings.Secure.getString(contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        } catch (e: SecurityException) {
            null
        }
        val isDefault = current?.startsWith("$packageName/") == true
        return (if (enabled) "✓ מופעלת" else "✗ לא מופעלת") + "\n" +
            (if (isDefault) "✓ ברירת מחדל" else "✗ לא ברירת מחדל")
    }

    private fun openKeyboardSettings() {
        try {
            startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
        } catch (e: Exception) {
            status.text = "אין בשעון מסך הגדרות מקלדת. השתמשו ב-install.sh"
        }
    }
}
