package com.salikoon.emulator8086.ui

import android.content.Intent
import android.os.Bundle
import org.json.JSONObject
import androidx.appcompat.app.AppCompatActivity

class TemplateActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = UiKit.screen(this, "Assembly templates", back = true)
        val body = UiKit.scrollContent(this, root)
        body.addView(UiKit.text(this, "Choose an example to load it into the editor. You can modify it before running.", 14, 0xFF747687.toInt()))
        var loadError: String? = null
        try {
            val json = resources.openRawResource(com.salikoon.emulator8086.R.raw.templates).bufferedReader().use { it.readText() }
            val templates = JSONObject(json).getJSONArray("Templates")
            for (i in 0 until templates.length()) {
                val item = templates.getJSONObject(i)
                val name = item.optString("Title", "Example ${i + 1}")
                val code = item.optString("Description", "")
                UiKit.card(this, body, name, code.lineSequence().firstOrNull().orEmpty()) {
                    startActivity(Intent(this, EditorActivity::class.java).putExtra(AppKeys.CODE, code).putExtra(AppKeys.TITLE, name))
                    finish()
                }
            }
        } catch (e: Exception) {
            loadError = "Could not load templates: ${e.message ?: "unknown error"}"
        }
        val status = UiKit.statusBar(this, root)
        loadError?.let { UiKit.showError(this, status, it) }
    }
}
