package com.salikoon.emulator8086.ui

import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.salikoon.emulator8086.engine.AssemblyEngine
import com.salikoon.emulator8086.engine.ExecutionSnapshot

class EmulateActivity : AppCompatActivity() {
    private lateinit var source: String
    private lateinit var input: EditText
    private lateinit var current: TextView
    private lateinit var registers: TextView
    private lateinit var flags: TextView
    private lateinit var output: TextView
    private lateinit var status: TextView
    private var engine: AssemblyEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        source = intent.getStringExtra(AppKeys.CODE).orEmpty()
        val root = UiKit.screen(this, "8086 Emulator", back = true)
        root.addView(UiKit.sectionLabel(this, "LIVE CPU SESSION"))
        root.addView(UiKit.text(this, intent.getStringExtra(AppKeys.TITLE) ?: "Program state", 13, UiKit.MUTED, true))
        input = EditText(this).apply {
            hint = "Optional character input · INT 21h / AH=1"
            setHintTextColor(UiKit.MUTED)
            setTextColor(UiKit.INK)
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 13f
            setSingleLine(true)
            setPadding(UiKit.dp(this@EmulateActivity, 14), 0, UiKit.dp(this@EmulateActivity, 14), 0)
            background = UiKit.surface(this@EmulateActivity)
        }
        root.addView(input, LinearLayout.LayoutParams(-1, UiKit.dp(this@EmulateActivity, 50)).apply { bottomMargin = UiKit.dp(this@EmulateActivity, 8) })
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(UiKit.button(this, "Step") { executeStep() }, LinearLayout.LayoutParams(0, UiKit.dp(this@EmulateActivity, 48), 1f).apply { rightMargin = UiKit.dp(this@EmulateActivity, 4) })
        controls.addView(UiKit.button(this, "Run ▶") { runToEnd() }, LinearLayout.LayoutParams(0, UiKit.dp(this@EmulateActivity, 48), 1f).apply { leftMargin = UiKit.dp(this@EmulateActivity, 4); rightMargin = UiKit.dp(this@EmulateActivity, 4) })
        controls.addView(UiKit.button(this, "Reset", secondary = true) { reset() }, LinearLayout.LayoutParams(0, UiKit.dp(this@EmulateActivity, 48), 1f).apply { leftMargin = UiKit.dp(this@EmulateActivity, 4) })
        root.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = UiKit.dp(this@EmulateActivity, 8); bottomMargin = UiKit.dp(this@EmulateActivity, 12) })
        val body = UiKit.scrollContent(this, root)
        current = UiKit.text(this, "", 14, UiKit.PURPLE, true).apply {
            setPadding(UiKit.dp(this@EmulateActivity, 14), UiKit.dp(this@EmulateActivity, 12), UiKit.dp(this@EmulateActivity, 14), UiKit.dp(this@EmulateActivity, 12))
            background = UiKit.surface(this@EmulateActivity)
        }
        body.addView(UiKit.sectionLabel(this, "INSTRUCTION POINTER"))
        body.addView(current, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiKit.dp(this@EmulateActivity, 7) })
        body.addView(UiKit.sectionLabel(this, "REGISTERS · HEX / DECIMAL"))
        registers = UiKit.text(this, "", 14, 0xFFDCE7FF.toInt()).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(UiKit.dp(this@EmulateActivity, 15), UiKit.dp(this@EmulateActivity, 14), UiKit.dp(this@EmulateActivity, 15), UiKit.dp(this@EmulateActivity, 14))
            background = UiKit.surface(this@EmulateActivity, dark = true)
        }
        body.addView(registers)
        body.addView(UiKit.sectionLabel(this, "STATUS FLAGS"))
        flags = UiKit.text(this, "", 13, 0xFF087E77.toInt(), true).apply {
            setPadding(UiKit.dp(this@EmulateActivity, 12), UiKit.dp(this@EmulateActivity, 10), UiKit.dp(this@EmulateActivity, 12), UiKit.dp(this@EmulateActivity, 10))
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = UiKit.dp(this@EmulateActivity, 13).toFloat(); setColor(0xFFE5F7F4.toInt()) }
        }
        body.addView(flags)
        body.addView(UiKit.sectionLabel(this, "PROGRAM OUTPUT"))
        output = UiKit.text(this, "", 15, 0xFF8AF0B0.toInt()).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(UiKit.dp(this@EmulateActivity, 14), UiKit.dp(this@EmulateActivity, 12), UiKit.dp(this@EmulateActivity, 14), UiKit.dp(this@EmulateActivity, 12))
            background = UiKit.surface(this@EmulateActivity, dark = true)
        }
        body.addView(output)
        status = UiKit.statusBar(this, root)
        reset()
    }

    private fun reset() {
        engine = try { AssemblyEngine(source, input.text.toString()) }
        catch (e: Exception) { UiKit.showError(this, status, e.message ?: "Invalid program."); null }
        engine?.snapshot()?.let { UiKit.clearStatus(status); render(it) }
    }

    private fun executeStep() {
        val cpu = engine ?: return
        try { render(cpu.step()); UiKit.clearStatus(status) }
        catch (e: Exception) { UiKit.showError(this, status, "Execution stopped: ${e.message ?: "unknown error"}") }
    }

    private fun runToEnd() {
        val cpu = engine ?: return
        try { render(cpu.run(3_000)); UiKit.clearStatus(status) }
        catch (e: Exception) { render(cpu.snapshot()); UiKit.showError(this, status, "Execution stopped: ${e.message ?: "unknown error"}") }
    }

    private fun render(state: ExecutionSnapshot) {
        current.text = if (state.finished) "${if (state.halted) "HLT" else "Finished"} · source line ${state.sourceLine}" else "Line ${state.sourceLine}:  ${state.instruction}"
        registers.text = state.registers.entries.joinToString("\n") { (name, value) -> "$name   ${value.toString().padStart(5)}   0x${value.toString(16).uppercase().padStart(4, '0')}" }
        flags.text = state.flags.entries.joinToString("   ") { (name, value) -> "$name=$value" }
        output.text = if (state.output.isEmpty()) "(no output)" else state.output
    }
}
