package com.salikoon.emulator8086.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Typeface
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var sizeValue: TextView
    private lateinit var preview: TextView
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = UiKit.screen(this, "Settings", back = true)
        val body = UiKit.scrollContent(this, root)
        body.addView(UiKit.sectionLabel(this, "EDITOR APPEARANCE"))
        body.addView(UiKit.text(this, "Tune your code canvas", 21, UiKit.INK, true))
        body.addView(UiKit.text(this, "Choose a comfortable size. The preview updates instantly.", 14, UiKit.MUTED))
        val prefs = getSharedPreferences("emulator", MODE_PRIVATE)
        sizeValue = UiKit.text(this, "${prefs.getInt("font_size", 16)} sp", 27, UiKit.PURPLE, true).apply { gravity = android.view.Gravity.CENTER }
        body.addView(sizeValue, LinearLayout.LayoutParams(-1, UiKit.dp(this, 62)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER }
        row.addView(UiKit.button(this, "−   Smaller", secondary = true) { adjust(-1) }, LinearLayout.LayoutParams(0, UiKit.dp(this, 48), 1f).apply { rightMargin = UiKit.dp(this@SettingsActivity, 6) })
        row.addView(UiKit.button(this, "Larger   +") { adjust(1) }, LinearLayout.LayoutParams(0, UiKit.dp(this, 48), 1f).apply { leftMargin = UiKit.dp(this@SettingsActivity, 6) })
        body.addView(row)
        UiKit.spacer(this, body, 14)
        body.addView(UiKit.sectionLabel(this, "LIVE PREVIEW"))
        preview = UiKit.text(this, "MOV AX, 16\nADD AX, 2\nHLT", prefs.getInt("font_size", 16), 0xFFDCE7FF.toInt()).apply {
            typeface = Typeface.MONOSPACE
            setPadding(UiKit.dp(this@SettingsActivity, 16), UiKit.dp(this@SettingsActivity, 15), UiKit.dp(this@SettingsActivity, 16), UiKit.dp(this@SettingsActivity, 15))
            background = UiKit.surface(this@SettingsActivity, dark = true)
        }
        body.addView(preview)
        UiKit.spacer(this, body, 14)
        body.addView(UiKit.button(this, "Save appearance") {
            prefs.edit().putInt("font_size", sizeValue.text.toString().substringBefore(' ').toInt()).apply()
            UiKit.showSuccess(this, status, "Editor appearance saved.")
        })
        status = UiKit.statusBar(this, root)
    }
    private fun adjust(delta: Int) {
        val next = (sizeValue.text.toString().substringBefore(' ').toInt() + delta).coerceIn(10, 25)
        sizeValue.text = "$next sp"
        preview.textSize = next.toFloat()
    }
}
