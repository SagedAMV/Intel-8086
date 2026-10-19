package com.salikoon.emulator8086.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = UiKit.screen(this, "About", back = true)
        val body = UiKit.scrollContent(this, root)
        body.addView(UiKit.text(this, "Intel 8086 Emulator", 22, 0xFF4A00E0.toInt(), true))
        body.addView(UiKit.text(this, "A native Kotlin learning environment for practicing 16-bit Assembly on Android.", 16))
        UiKit.spacer(this, body, 12)
        UiKit.card(this, body, "Write", "Edit programs with a compact, monospaced Assembly editor.") {}
        UiKit.card(this, body, "Emulate", "Step through instructions and inspect 8086 registers, flags, and character I/O.") {}
        UiKit.card(this, body, "Learn", "Explore the built-in example templates and instruction reference.") {}
        body.addView(UiKit.text(this, "Version 2.0.0 · Kotlin Android port", 13, 0xFF747687.toInt()))
    }
}
