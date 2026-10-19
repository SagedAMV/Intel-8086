package com.salikoon.emulator8086.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView
import android.widget.LinearLayout

internal object AppKeys {
    const val CODE = "assembly.code"
    const val TITLE = "assembly.title"
    const val URI = "assembly.uri"
}

class MainActivity : AppCompatActivity() {
    private val pickFile = 41
    private lateinit var status: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = UiKit.screen(this, "8086 Emulator")
        val body = UiKit.scrollContent(this, root)
        body.addView(UiKit.sectionLabel(this, "YOUR ASSEMBLY WORKSPACE"))
        body.addView(UiKit.text(this, "Build something\nbrilliant.", 25, UiKit.INK, true))
        body.addView(UiKit.text(this, "Write 16-bit Assembly, run it step by step, and see exactly what your CPU is doing.", 14, UiKit.MUTED))
        UiKit.spacer(this, body, 8)
        val prefs = getSharedPreferences("emulator", MODE_PRIVATE)
        val recent = prefs.getString("recent_code", null)
        if (!recent.isNullOrBlank()) {
            body.addView(UiKit.sectionLabel(this, "CONTINUE YOUR LAST SESSION"))
            UiKit.card(this, body, prefs.getString("recent_title", "Recent program") ?: "Recent program", "Pick up where you left off") {
                openEditor(recent, prefs.getString("recent_title", "Untitled") ?: "Untitled")
            }
        }
        body.addView(UiKit.sectionLabel(this, "QUICK ACTIONS"))
        val firstRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        UiKit.squareCard(this, firstRow, "New program", "Start from scratch", "＋") { openEditor() }
        UiKit.squareCard(this, firstRow, "Open file", "Choose an .asm file", "↗") { chooseFile() }
        body.addView(firstRow, LinearLayout.LayoutParams(-1, -2))
        val secondRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        UiKit.squareCard(this, secondRow, "Templates", "Explore examples", "▤") { startActivity(Intent(this, TemplateActivity::class.java)) }
        UiKit.squareCard(this, secondRow, "Instruction help", "Browse the guide", "?") { startActivity(Intent(this, HelpActivity::class.java)) }
        body.addView(secondRow, LinearLayout.LayoutParams(-1, -2))
        val thirdRow = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        UiKit.squareCard(this, thirdRow, "Settings", "Tune your editor", "⚙") { startActivity(Intent(this, SettingsActivity::class.java)) }
        UiKit.squareCard(this, thirdRow, "About", "About the 8086 lab", "ⓘ") { startActivity(Intent(this, AboutActivity::class.java)) }
        body.addView(thirdRow, LinearLayout.LayoutParams(-1, -2))
        status = UiKit.statusBar(this, root)
    }

    private fun openEditor(code: String = "", title: String = "Untitled") {
        startActivity(Intent(this, EditorActivity::class.java).putExtra(AppKeys.CODE, code).putExtra(AppKeys.TITLE, title))
    }

    private fun chooseFile() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/*"
        }
        try { startActivityForResult(intent, pickFile) }
        catch (e: Exception) { UiKit.showError(this, status, "No file picker is available: ${e.message ?: "unknown error"}") }
    }

    @Deprecated("Handled for compatibility with the minimum supported Android version")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != pickFile || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            val text = contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
            val title = uri.lastPathSegment?.substringAfterLast('/')?.substringAfterLast(':') ?: "Assembly file"
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            openEditor(text, title)
        } catch (e: Exception) { UiKit.showError(this, status, "Unable to read file: ${e.message ?: "unknown error"}") }
    }
}
