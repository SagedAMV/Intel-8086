package com.salikoon.emulator8086.ui

import android.content.Intent
import android.graphics.Typeface
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.text.InputType
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.salikoon.emulator8086.engine.AssemblyEngine

class EditorActivity : AppCompatActivity() {
    private lateinit var editor: EditText
    private lateinit var status: TextView
    private var title = "Untitled"
    private var keyboardVisible = false
    private var imeVisibleFromInsets = false
    private val visibleFrame = Rect()
    private val saveRequest = 52

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        title = intent.getStringExtra(AppKeys.TITLE) ?: "Untitled"
        val root = UiKit.screen(this, "Assembly editor", back = true)
        val fileHeader = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = android.view.Gravity.CENTER_VERTICAL }
        fileHeader.addView(UiKit.sectionLabel(this, "SOURCE CODE"), LinearLayout.LayoutParams(0, -2, 1f))
        fileHeader.addView(UiKit.text(this, title, 12, UiKit.MUTED, true).apply { gravity = android.view.Gravity.END })
        root.addView(fileHeader, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiKit.dp(this@EditorActivity, 2) })
        val actions = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        actions.addView(UiKit.button(this, "Validate", secondary = true) { validateProgram() }, LinearLayout.LayoutParams(0, UiKit.dp(this@EditorActivity, 48), 1f).apply { rightMargin = UiKit.dp(this@EditorActivity, 6) })
        actions.addView(UiKit.button(this, "Save", secondary = true) { saveFile() }, LinearLayout.LayoutParams(0, UiKit.dp(this@EditorActivity, 48), 1f).apply { leftMargin = UiKit.dp(this@EditorActivity, 6); rightMargin = UiKit.dp(this@EditorActivity, 6) })
        actions.addView(UiKit.button(this, "Run ▶") { runProgram() }, LinearLayout.LayoutParams(0, UiKit.dp(this@EditorActivity, 48), 1f).apply { leftMargin = UiKit.dp(this@EditorActivity, 6) })
        root.addView(actions, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = UiKit.dp(this@EditorActivity, 12) })

        editor = EditText(this).apply {
            setText(intent.getStringExtra(AppKeys.CODE).orEmpty())
            setTextColor(0xFFE5ECFF.toInt()); setHintTextColor(0xFF8E9AB9.toInt()); hint = "MOV AX, 6\nADD AX, 4\nHLT"
            textSize = getSharedPreferences("emulator", MODE_PRIVATE).getInt("font_size", 16).toFloat()
            typeface = Typeface.MONOSPACE; setPadding(UiKit.dp(this@EditorActivity, 14), UiKit.dp(this@EditorActivity, 14), UiKit.dp(this@EditorActivity, 14), UiKit.dp(this@EditorActivity, 14))
            setBackgroundColor(android.graphics.Color.TRANSPARENT); gravity = android.view.Gravity.TOP or android.view.Gravity.START
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS or InputType.TYPE_TEXT_FLAG_CAP_CHARACTERS
            setHorizontallyScrolling(true); isVerticalScrollBarEnabled = true
        }
        val editorScroll = ScrollView(this).apply {
            addView(editor); isFillViewport = true; clipToPadding = false
            setPadding(UiKit.dp(this@EditorActivity, 4), UiKit.dp(this@EditorActivity, 4), UiKit.dp(this@EditorActivity, 4), UiKit.dp(this@EditorActivity, 4))
        }
        val editorSurface = FrameLayout(this).apply {
            background = UiKit.surface(this@EditorActivity, dark = true)
            setPadding(UiKit.dp(this@EditorActivity, 8), UiKit.dp(this@EditorActivity, 8), UiKit.dp(this@EditorActivity, 8), UiKit.dp(this@EditorActivity, 8))
            addView(editorScroll, FrameLayout.LayoutParams(-1, -1))
        }
        root.addView(editorSurface, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = UiKit.dp(this@EditorActivity, 6) })
        root.addView(UiKit.text(this, "Supported: MOV · arithmetic · logic · jumps · loops · flags · INT 21h", 11, UiKit.MUTED))
        status = UiKit.statusBar(this, root)
        ViewCompat.setOnApplyWindowInsetsListener(root) { _, insets ->
            imeVisibleFromInsets = insets.isVisible(WindowInsetsCompat.Type.ime())
            refreshKeyboardVisibility()
            insets
        }
        root.viewTreeObserver.addOnGlobalLayoutListener { refreshKeyboardVisibility() }
        ViewCompat.requestApplyInsets(root)
    }

    /** IME insets are inconsistent on some vendor keyboards; visible-frame height is the fallback. */
    private fun refreshKeyboardVisibility() {
        window.decorView.getWindowVisibleDisplayFrame(visibleFrame)
        val screenHeight = resources.displayMetrics.heightPixels
        val hiddenHeight = screenHeight - visibleFrame.bottom
        val threshold = maxOf(UiKit.dp(this, 120), screenHeight / 6)
        val detectedByFrame = hiddenHeight > threshold
        val detected = imeVisibleFromInsets || detectedByFrame
        if (keyboardVisible != detected) {
            keyboardVisible = detected
            syncStatusWithKeyboard()
        }
    }

    private fun syncStatusWithKeyboard() {
        if (!::status.isInitialized) return
        if (keyboardVisible || status.text.isNullOrBlank()) {
            status.animate().cancel()
            status.alpha = 1f
            status.translationY = 0f
            status.visibility = View.GONE
        } else {
            status.visibility = View.VISIBLE
        }
    }

    private fun showError(message: String) {
        refreshKeyboardVisibility()
        UiKit.showError(this, status, message)
        syncStatusWithKeyboard()
    }

    private fun showSuccess(message: String) {
        refreshKeyboardVisibility()
        UiKit.showSuccess(this, status, message)
        syncStatusWithKeyboard()
    }

    private fun validateProgram() {
        val code = editor.text.toString()
        if (code.isBlank()) { showError("Write some code first."); return }
        val issues = AssemblyEngine.validate(code)
        if (issues.isEmpty()) showSuccess("Syntax and labels look good.")
        else showError(formatIssues(issues))
    }

    private fun formatIssues(issues: List<com.salikoon.emulator8086.engine.ValidationIssue>): String = buildString {
        append("Found ${issues.size} issue(s):")
        issues.forEach { issue -> append("\nLine ${issue.line}: ${issue.message}") }
    }

    private fun runProgram() {
        val code = editor.text.toString()
        if (code.isBlank()) { showError("Write some code first."); return }
        val issues = AssemblyEngine.validate(code)
        if (issues.isNotEmpty()) { showError(formatIssues(issues)); return }
        UiKit.clearStatus(status)
        getSharedPreferences("emulator", MODE_PRIVATE).edit().putString("recent_code", code).putString("recent_title", title).apply()
        startActivity(Intent(this, EmulateActivity::class.java).putExtra(AppKeys.CODE, code).putExtra(AppKeys.TITLE, title))
    }

    private fun saveFile() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE); type = "text/plain"; putExtra(Intent.EXTRA_TITLE, if (title.endsWith(".asm")) title else "$title.asm")
        }
        try { startActivityForResult(intent, saveRequest) }
        catch (e: Exception) { showError("Unable to open the save dialog: ${e.message ?: "unknown error"}") }
    }

    @Deprecated("Handled for compatibility with the minimum supported Android version")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != saveRequest || resultCode != RESULT_OK) return
        val uri: Uri = data?.data ?: return
        try {
            contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(editor.text.toString()) }
            title = uri.lastPathSegment?.substringAfterLast(':') ?: title
            showSuccess("Program saved.")
        } catch (e: Exception) { showError("Unable to save: ${e.message ?: "unknown error"}") }
    }
}
