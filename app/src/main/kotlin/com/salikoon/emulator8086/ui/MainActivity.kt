package com.salikoon.emulator8086.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.TextView

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
        body.addView(UiKit.text(this, "Your mobile Assembly workbench", 19, 0xFF20212B.toInt(), true))
        body.addView(UiKit.text(this, "Create a program, explore a template, then inspect every 8086 step.", 14, 0xFF747687.toInt()))
        UiKit.spacer(this, body, 12)
        UiKit.card(this, body, "Create new program", "Start with an empty Assembly file") { openEditor() }
        UiKit.card(this, body, "Open a program", "Choose a text or .asm file from this device") { chooseFile() }
        UiKit.card(this, body, "Assembly templates", "Start from worked examples and loops") { startActivity(Intent(this, TemplateActivity::class.java)) }
        UiKit.card(this, body, "Help & instruction reference", "Learn supported instructions and syntax") { startActivity(Intent(this, HelpActivity::class.java)) }
        val prefs = getSharedPreferences("emulator", MODE_PRIVATE)
        val recent = prefs.getString("recent_code", null)
        if (!recent.isNullOrBlank()) {
            UiKit.spacer(this, body, 4)
            body.addView(UiKit.text(this, "Continue", 17, 0xFF20212B.toInt(), true))
            UiKit.card(this, body, prefs.getString("recent_title", "Recent program") ?: "Recent program", "Reopen the last program",) {
                openEditor(recent, prefs.getString("recent_title", "Untitled") ?: "Untitled")
            }
        }
        UiKit.spacer(this, body, 12)
        body.addView(UiKit.button(this, "Settings", secondary = true) { startActivity(Intent(this, SettingsActivity::class.java)) })
        UiKit.spacer(this, body, 6)
        body.addView(UiKit.button(this, "About 8086 Emulator", secondary = true) { startActivity(Intent(this, AboutActivity::class.java)) })
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
