package com.salikoon.emulator8086.ui

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

internal object UiKit {
    private const val PURPLE = 0xFF6F2DBD.toInt()
    private const val BLUE = 0xFF4527A0.toInt()
    private const val INK = 0xFF20212B.toInt()
    private const val MUTED = 0xFF747687.toInt()
    private const val ERROR = 0xFFB42318.toInt()
    private const val SUCCESS = 0xFF16794B.toInt()

    fun dp(activity: AppCompatActivity, value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    fun screen(activity: AppCompatActivity, title: String, back: Boolean = false): LinearLayout {
        activity.window.statusBarColor = 0xFF371366.toInt()
        activity.window.navigationBarColor = 0xFFF8F7FC.toInt()
        activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 14), dp(activity, 18), dp(activity, 12))
            setBackgroundColor(Color.rgb(248, 247, 252))
        }
        val hero = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 14), dp(activity, 18), dp(activity, 17))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF4527A0.toInt(), 0xFF8E2DE2.toInt())).apply { cornerRadius = dp(activity, 24).toFloat() }
            elevation = dp(activity, 3).toFloat()
        }
        if (back) hero.addView(button(activity, "←   Back", secondary = true) { activity.finish() }, LinearLayout.LayoutParams(-2, dp(activity, 40)).apply { bottomMargin = dp(activity, 10) })
        hero.addView(text(activity, "8086 EMULATOR  ·  ASSEMBLY LAB", 10, 0xFFE5D8FF.toInt(), true))
        hero.addView(text(activity, title, 25, Color.WHITE, true))
        hero.addView(text(activity, "Write  ·  Execute  ·  Inspect", 12, 0xFFF3EDFF.toInt()))
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(activity, 14) })
        activity.setContentView(root)
        return root
    }

    fun scrollContent(activity: AppCompatActivity, parent: LinearLayout): LinearLayout {
        val scroll = ScrollView(activity).apply { isFillViewport = true; clipToPadding = false; overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS }
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(activity, 8)) }
        scroll.addView(body)
        parent.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        return body
    }

    fun text(activity: AppCompatActivity, value: CharSequence, size: Int = 16, color: Int = INK, bold: Boolean = false): TextView = TextView(activity).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(activity, 2), dp(activity, 5), dp(activity, 2), dp(activity, 5))
    }

    fun button(activity: AppCompatActivity, label: String, secondary: Boolean = false, action: () -> Unit): Button = Button(activity).apply {
        text = label; textSize = 14f; isAllCaps = false; setTextColor(if (secondary) 0xFF4527A0.toInt() else Color.WHITE)
        background = GradientDrawable(
            if (secondary) GradientDrawable.Orientation.LEFT_RIGHT else GradientDrawable.Orientation.BL_TR,
            if (secondary) intArrayOf(0xFFFFFFFF.toInt(), 0xFFF1ECF8.toInt()) else intArrayOf(PURPLE, BLUE)
        ).apply { cornerRadius = dp(activity, 14).toFloat() }
        elevation = if (secondary) dp(activity, 0).toFloat() else dp(activity, 2).toFloat()
        setPadding(dp(activity, 14), dp(activity, 5), dp(activity, 14), dp(activity, 5))
        setOnClickListener { action() }
    }

    fun card(activity: AppCompatActivity, parent: LinearLayout, title: String, detail: String, action: () -> Unit) {
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val badgeText = when {
            title.contains("Create", true) -> "+"
            title.contains("Open", true) -> "↥"
            title.contains("template", true) -> "▤"
            title.contains("help", true) || title.contains("instruction", true) -> "?"
            title.contains("setting", true) -> "⚙"
            title.contains("recent", true) || title.contains("continue", true) -> "↻"
            else -> "⌁"
        }
        val badge = TextView(activity).apply {
            text = badgeText; textSize = 22f; gravity = Gravity.CENTER; setTextColor(0xFF6F2DBD.toInt())
            background = GradientDrawable().apply { cornerRadius = dp(activity, 14).toFloat(); setColor(0xFFF0E9FC.toInt()) }
        }
        row.addView(badge, LinearLayout.LayoutParams(dp(activity, 50), dp(activity, 50)).apply { rightMargin = dp(activity, 13) })
        val copy = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(text(activity, title, 16, INK, true))
        copy.addView(text(activity, detail, 13, MUTED))
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text(activity, "›", 25, 0xFF8E2DE2.toInt(), true))
        val card = LinearLayout(activity).apply {
            setPadding(dp(activity, 13), dp(activity, 12), dp(activity, 14), dp(activity, 12))
            background = GradientDrawable().apply { cornerRadius = dp(activity, 19).toFloat(); setColor(Color.WHITE); setStroke(dp(activity, 1), 0xFFE9E6F0.toInt()) }
            elevation = dp(activity, 2).toFloat(); isClickable = true; isFocusable = true; setOnClickListener { action() }
            addView(row)
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(activity, 11) })
    }

    /** Persistent, in-layout feedback pinned below scrollable content (never a Toast/snackbar). */
    fun statusBar(activity: AppCompatActivity, parent: LinearLayout): TextView = TextView(activity).apply {
        visibility = View.GONE; textSize = 14f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(activity, 13), dp(activity, 9), dp(activity, 13), dp(activity, 9))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(activity, 8) })
    }

    fun showError(activity: AppCompatActivity, bar: TextView, message: String) {
        bar.text = "⚠  $message"; bar.setTextColor(ERROR)
        bar.background = statusBackground(activity, 0xFFFDECEA.toInt(), 0xFFF5B9B2.toInt())
        bar.visibility = View.VISIBLE
    }

    fun showSuccess(activity: AppCompatActivity, bar: TextView, message: String) {
        bar.text = "✓  $message"; bar.setTextColor(SUCCESS)
        bar.background = statusBackground(activity, 0xFFE8F5EE.toInt(), 0xFFB7E1C8.toInt())
        bar.visibility = View.VISIBLE
    }

    fun clearStatus(bar: TextView) { bar.visibility = View.GONE; bar.text = "" }

    private fun statusBackground(activity: AppCompatActivity, fill: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = dp(activity, 12).toFloat(); setColor(fill); setStroke(dp(activity, 1), stroke)
    }

    fun spacer(activity: AppCompatActivity, parent: LinearLayout, height: Int = 10) {
        parent.addView(View(activity), LinearLayout.LayoutParams(1, dp(activity, height)))
    }
}
