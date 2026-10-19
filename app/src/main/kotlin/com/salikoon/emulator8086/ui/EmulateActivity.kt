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
        root.addView(UiKit.text(this, intent.getStringExtra(AppKeys.TITLE) ?: "Program state", 14, 0xFF747687.toInt()))
        input = EditText(this).apply {
            hint = "Input character for INT 21h / AH=1 (optional)"
            inputType = InputType.TYPE_CLASS_TEXT
            textSize = 14f
            setSingleLine(true)
        }
        root.addView(input, LinearLayout.LayoutParams(-1, UiKit.dp(this@EmulateActivity, 52)))
        val controls = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        controls.addView(UiKit.button(this, "Step") { executeStep() }, LinearLayout.LayoutParams(0, UiKit.dp(this@EmulateActivity, 48), 1f).apply { rightMargin = UiKit.dp(this@EmulateActivity, 4) })
        controls.addView(UiKit.button(this, "Run ▶") { runToEnd() }, LinearLayout.LayoutParams(0, UiKit.dp(this@EmulateActivity, 48), 1f).apply { leftMargin = UiKit.dp(this@EmulateActivity, 4); rightMargin = UiKit.dp(this@EmulateActivity, 4) })
        controls.addView(UiKit.button(this, "Reset", secondary = true) { reset() }, LinearLayout.LayoutParams(0, UiKit.dp(this@EmulateActivity, 48), 1f).apply { leftMargin = UiKit.dp(this@EmulateActivity, 4) })
        root.addView(controls, LinearLayout.LayoutParams(-1, -2).apply { topMargin = UiKit.dp(this@EmulateActivity, 8); bottomMargin = UiKit.dp(this@EmulateActivity, 12) })
        val body = UiKit.scrollContent(this, root)
        current = UiKit.text(this, "", 14, 0xFF4A00E0.toInt(), true)
        body.addView(current)
        body.addView(UiKit.text(this, "REGISTERS", 13, 0xFF747687.toInt(), true))
        registers = UiKit.text(this, "", 15, 0xFF20212B.toInt()).apply {
            typeface = android.graphics.Typeface.MONOSPACE
            setPadding(UiKit.dp(this@EmulateActivity, 14), UiKit.dp(this@EmulateActivity, 12), UiKit.dp(this@EmulateActivity, 14), UiKit.dp(this@EmulateActivity, 12))
            background = android.graphics.drawable.GradientDrawable().apply { cornerRadius = UiKit.dp(this@EmulateActivity, 14).toFloat(); setColor(android.graphics.Color.WHITE) }
        }
        body.addView(registers)
        body.addView(UiKit.text(this, "FLAGS", 13, 0xFF747687.toInt(), true))
        flags = UiKit.text(this, "", 14, 0xFF20212B.toInt())
        body.addView(flags)
        body.addView(UiKit.text(this, "OUTPUT", 13, 0xFF747687.toInt(), true))
        output = UiKit.text(this, "", 16, 0xFF20212B.toInt())
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
