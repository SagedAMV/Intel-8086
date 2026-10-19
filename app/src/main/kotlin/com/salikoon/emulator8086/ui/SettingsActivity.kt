package com.salikoon.emulator8086.ui

import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SettingsActivity : AppCompatActivity() {
    private lateinit var sizeValue: TextView
    private lateinit var status: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = UiKit.screen(this, "Settings", back = true)
        val body = UiKit.scrollContent(this, root)
        body.addView(UiKit.text(this, "Editor text size", 17, 0xFF20212B.toInt(), true))
        body.addView(UiKit.text(this, "Choose a comfortable font size for Assembly code.", 14, 0xFF747687.toInt()))
        val prefs = getSharedPreferences("emulator", MODE_PRIVATE)
        sizeValue = UiKit.text(this, prefs.getInt("font_size", 16).toString(), 30, 0xFF4A00E0.toInt(), true).apply { gravity = android.view.Gravity.CENTER }
        body.addView(sizeValue, LinearLayout.LayoutParams(-1, UiKit.dp(this, 64)))
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER }
        row.addView(UiKit.button(this, "−", secondary = true) { adjust(-1) }, LinearLayout.LayoutParams(UiKit.dp(this, 84), UiKit.dp(this, 48)).apply { rightMargin = UiKit.dp(this@SettingsActivity, 12) })
        row.addView(UiKit.button(this, "+") { adjust(1) }, LinearLayout.LayoutParams(UiKit.dp(this, 84), UiKit.dp(this, 48)))
        body.addView(row)
        UiKit.spacer(this, body, 18)
        body.addView(UiKit.button(this, "Save settings") {
            prefs.edit().putInt("font_size", sizeValue.text.toString().toInt()).apply()
            UiKit.showSuccess(this, status, "Settings saved.")
        })
        status = UiKit.statusBar(this, root)
    }
    private fun adjust(delta: Int) {
        val next = (sizeValue.text.toString().toInt() + delta).coerceIn(10, 25)
        sizeValue.text = next.toString()
    }
}
