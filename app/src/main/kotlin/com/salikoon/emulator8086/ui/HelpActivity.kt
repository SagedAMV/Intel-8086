package com.salikoon.emulator8086.ui

import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject

class HelpActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = UiKit.screen(this, "Help & instructions", back = true)
        val body = UiKit.scrollContent(this, root)
        body.addView(UiKit.sectionLabel(this, "QUICK REFERENCE"))
        body.addView(UiKit.text(this, "The instruction field guide", 21, UiKit.INK, true))
        body.addView(UiKit.text(this, "Tap an instruction for syntax and examples. Use one instruction per line, end labels with a colon, and start comments with a semicolon.", 14, UiKit.MUTED))
        body.addView(UiKit.text(this, "Registers: AX, BX, CX, DX, SI, DI, BP, SP. Byte aliases: AH/AL, BH/BL, CH/CL, DH/DL. Numbers may be decimal, binary (…B), or hexadecimal (…H / 0x…).", 13, UiKit.INK).apply {
            setPadding(UiKit.dp(this@HelpActivity, 14), UiKit.dp(this@HelpActivity, 12), UiKit.dp(this@HelpActivity, 14), UiKit.dp(this@HelpActivity, 12))
            background = UiKit.surface(this@HelpActivity)
        })
        body.addView(UiKit.sectionLabel(this, "AVAILABLE INSTRUCTIONS"))
        var loadError: String? = null
        try {
            val raw = resources.openRawResource(com.salikoon.emulator8086.R.raw.help_manual).bufferedReader().use { it.readText() }
            val instructions = JSONObject(raw).getJSONArray("InstructionList")
            for (i in 0 until instructions.length()) {
                val item = instructions.getJSONObject(i)
                val name = item.optString("Instruction", "Instruction")
                val syntax = item.optString("Syntax", name)
                UiKit.card(this, body, name, syntax) { showDetails(item) }
            }
        } catch (e: Exception) { loadError = "Instruction reference unavailable: ${e.message ?: "unknown error"}" }
        body.addView(UiKit.text(this, "Runtime support includes data transfer, arithmetic and bitwise instructions, conditional jumps, LOOP, flag control, NOP, HLT, and DOS INT 21h character I/O. Unsupported directives are not assembled.", 13, UiKit.MUTED).apply {
            setPadding(UiKit.dp(this@HelpActivity, 12), UiKit.dp(this@HelpActivity, 10), UiKit.dp(this@HelpActivity, 12), UiKit.dp(this@HelpActivity, 10))
            background = UiKit.surface(this@HelpActivity)
        })
        val status = UiKit.statusBar(this, root)
        loadError?.let { UiKit.showError(this, status, it) }
    }

    private fun showDetails(item: JSONObject) {
        val content = buildString {
            append(item.optString("Syntax")); append("\n\n"); append(item.optString("Description"))
            listOf("Source", "Destination", "FlagsChanged").forEach { key ->
                val array = item.optJSONArray(key) ?: return@forEach
                if (array.length() > 0) { append("\n\n$key: "); append((0 until array.length()).joinToString(", ") { array.optString(it) }) }
            }
            item.optJSONArray("Examples")?.let { examples ->
                if (examples.length() > 0) { append("\n\nExamples:\n"); for (i in 0 until examples.length()) { val ex = examples.getJSONObject(i); append("• "); append(ex.optString("Specimen")); if (i < examples.length() - 1) append('\n') } }
            }
        }
        AlertDialog.Builder(this).setTitle(item.optString("Instruction")).setMessage(content).setPositiveButton("Close", null).show()
    }
}
