package com.salikoon.emulator8086.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = UiKit.screen(this, "About", back = true)
        val body = UiKit.scrollContent(this, root)
        body.addView(UiKit.sectionLabel(this, "MADE FOR THE CURIOUS"))
        body.addView(UiKit.text(this, "A pocket-sized\nAssembly lab.", 26, UiKit.INK, true))
        body.addView(UiKit.text(this, "A native Kotlin learning environment for exploring 16-bit Assembly, one instruction at a time.", 15, UiKit.MUTED))
        UiKit.spacer(this, body, 8)
        body.addView(UiKit.sectionLabel(this, "EXPLORE THE WORKFLOW"))
        UiKit.card(this, body, "Write", "Edit programs with a compact, monospaced Assembly editor.") {}
        UiKit.card(this, body, "Emulate", "Step through instructions and inspect 8086 registers, flags, and character I/O.") {}
        UiKit.card(this, body, "Learn", "Explore the built-in example templates and instruction reference.") {}
        body.addView(UiKit.text(this, "VERSION 2.0.0   ·   KOTLIN ANDROID", 11, UiKit.MUTED, true))
    }
}
