package com.salikoon.emulator8086.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/** Shared visual language for the app: soft canvas, ink-blue headers, vivid action color, and crisp surfaces. */
internal object UiKit {
    const val INK = 0xFF20253A.toInt()
    const val MUTED = 0xFF737B91.toInt()
    const val PURPLE = 0xFF5553E8.toInt()
    const val BLUE = 0xFF2878E5.toInt()
    const val CYAN = 0xFF36D6C5.toInt()
    private const val CANVAS = 0xFFF5F6FB.toInt()
    private const val ERROR = 0xFFB42318.toInt()
    private const val SUCCESS = 0xFF16794B.toInt()
    private val badgeColors = intArrayOf(0xFFE9E9FF.toInt(), 0xFFE0F7F4.toInt(), 0xFFFFF1DC.toInt(), 0xFFFFE8EF.toInt())
    private val badgeInks = intArrayOf(PURPLE, 0xFF078B83.toInt(), 0xFFB56B10.toInt(), 0xFFCA4867.toInt())

    fun dp(activity: AppCompatActivity, value: Int): Int = (value * activity.resources.displayMetrics.density).toInt()

    fun screen(activity: AppCompatActivity, title: String, back: Boolean = false): LinearLayout {
        activity.window.statusBarColor = 0xFF171D3B.toInt()
        activity.window.navigationBarColor = CANVAS
        @Suppress("DEPRECATION")
        run { activity.window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR }
        val root = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 18), dp(activity, 14), dp(activity, 18), dp(activity, 12))
            setBackgroundColor(CANVAS)
        }
        val hero = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 20), dp(activity, 16), dp(activity, 20), dp(activity, 20))
            background = GradientDrawable(GradientDrawable.Orientation.TL_BR, intArrayOf(0xFF171D3B.toInt(), 0xFF303A7A.toInt(), 0xFF514BE5.toInt())).apply {
                cornerRadius = dp(activity, 26).toFloat()
            }
            elevation = dp(activity, 5).toFloat()
            alpha = 0f
            translationY = dp(activity, 9).toFloat()
        }
        if (back) {
            val backButton = TextView(activity).apply {
                text = "‹   Back"; textSize = 13f; setTextColor(Color.WHITE); gravity = Gravity.CENTER
                setPadding(dp(activity, 12), 0, dp(activity, 12), 0)
                background = roundRect(activity, 0x26FFFFFF, 0x33FFFFFF)
                isClickable = true; isFocusable = true
                setOnClickListener { activity.finish() }
                pressMotion(this)
            }
            hero.addView(backButton, LinearLayout.LayoutParams(-2, dp(activity, 34)).apply { bottomMargin = dp(activity, 12) })
        }
        val brand = TextView(activity).apply {
            text = "8086  /  SYSTEM READY"; textSize = 10f; letterSpacing = .12f
            setTextColor(0xFFBFEDE9.toInt()); typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setPadding(dp(activity, 10), dp(activity, 5), dp(activity, 10), dp(activity, 5))
            background = roundRect(activity, 0x2436D6C5, 0x4036D6C5)
        }
        hero.addView(brand, LinearLayout.LayoutParams(-2, -2).apply { bottomMargin = dp(activity, 8) })
        hero.addView(text(activity, title, 27, Color.WHITE, true))
        hero.addView(text(activity, "ASSEMBLY  ·  16-BIT LEARNING LAB", 10, 0xFFD9DDF7.toInt(), true))
        root.addView(hero, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(activity, 16) })
        hero.post { hero.animate().alpha(1f).translationY(0f).setDuration(380).start() }
        activity.setContentView(root)
        return root
    }

    fun scrollContent(activity: AppCompatActivity, parent: LinearLayout): LinearLayout {
        val scroll = ScrollView(activity).apply { isFillViewport = true; clipToPadding = false; overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS }
        val body = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL; setPadding(0, 0, 0, dp(activity, 8)) }
        scroll.addView(body)
        parent.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        body.post {
            for (i in 0 until body.childCount) {
                val child = body.getChildAt(i)
                child.alpha = 0f
                child.translationY = dp(activity, 12).toFloat()
                child.animate().alpha(1f).translationY(0f).setStartDelay((i * 42L).coerceAtMost(280L)).setDuration(320).start()
            }
        }
        return body
    }

    fun text(activity: AppCompatActivity, value: CharSequence, size: Int = 16, color: Int = INK, bold: Boolean = false): TextView = TextView(activity).apply {
        text = value; textSize = size.toFloat(); setTextColor(color); if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(dp(activity, 2), dp(activity, 4), dp(activity, 2), dp(activity, 4))
        setLineSpacing(dp(activity, 2).toFloat(), 1f)
    }

    fun sectionLabel(activity: AppCompatActivity, value: String): TextView = text(activity, value.uppercase(), 11, MUTED, true).apply {
        letterSpacing = .1f
        setPadding(dp(activity, 2), dp(activity, 11), dp(activity, 2), dp(activity, 7))
    }

    fun button(activity: AppCompatActivity, label: String, secondary: Boolean = false, action: () -> Unit): Button = Button(activity).apply {
        text = label; textSize = 14f; isAllCaps = false; setTextColor(if (secondary) PURPLE else Color.WHITE)
        background = RippleDrawable(
            ColorStateList.valueOf(if (secondary) 0x1C5553E8 else 0x36FFFFFF),
            GradientDrawable(if (secondary) GradientDrawable.Orientation.LEFT_RIGHT else GradientDrawable.Orientation.BL_TR,
                if (secondary) intArrayOf(Color.WHITE, 0xFFEEF0FA.toInt()) else intArrayOf(PURPLE, BLUE)).apply { cornerRadius = dp(activity, 15).toFloat() },
            null
        )
        elevation = if (secondary) dp(activity, 1).toFloat() else dp(activity, 3).toFloat()
        setPadding(dp(activity, 14), dp(activity, 5), dp(activity, 14), dp(activity, 5))
        setOnClickListener { action() }
        pressMotion(this)
    }

    fun card(activity: AppCompatActivity, parent: LinearLayout, title: String, detail: String, action: () -> Unit) {
        val colorIndex = (title.hashCode().absoluteValueSafe() % badgeColors.size)
        val row = LinearLayout(activity).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        val badgeText = when {
            title.contains("Create", true) || title.contains("Write", true) -> "+"
            title.contains("Open", true) -> "↗"
            title.contains("template", true) -> "▤"
            title.contains("help", true) || title.contains("instruction", true) -> "?"
            title.contains("setting", true) -> "⚙"
            title.contains("recent", true) || title.contains("continue", true) -> "↻"
            title.contains("emulat", true) || title.contains("run", true) -> "▶"
            else -> "⌁"
        }
        val badge = TextView(activity).apply {
            text = badgeText; textSize = 21f; gravity = Gravity.CENTER; setTextColor(badgeInks[colorIndex])
            background = roundRect(activity, badgeColors[colorIndex], null)
        }
        row.addView(badge, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)).apply { marginEnd = dp(activity, 13) })
        val copy = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        copy.addView(text(activity, title, 15, INK, true))
        copy.addView(text(activity, detail, 12, MUTED))
        row.addView(copy, LinearLayout.LayoutParams(0, -2, 1f))
        row.addView(text(activity, "›", 25, badgeInks[colorIndex], true).apply { gravity = Gravity.CENTER })
        val card = LinearLayout(activity).apply {
            setPadding(dp(activity, 13), dp(activity, 12), dp(activity, 13), dp(activity, 12))
            background = RippleDrawable(ColorStateList.valueOf(0x145553E8), roundRect(activity, Color.WHITE, 0xFFE7E9F1.toInt()), null)
            elevation = dp(activity, 2).toFloat(); isClickable = true; isFocusable = true
            setOnClickListener { action() }; addView(row)
            pressMotion(this)
        }
        parent.addView(card, LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(activity, 10) })
    }

    /** Two-column home action tile with a 1:1 footprint on common phone widths. */
    fun squareCard(activity: AppCompatActivity, parent: LinearLayout, title: String, detail: String, icon: String, action: () -> Unit) {
        val colorIndex = title.hashCode().absoluteValueSafe() % badgeColors.size
        val tile = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.TOP or Gravity.START
            setPadding(dp(activity, 14), dp(activity, 14), dp(activity, 12), dp(activity, 12))
            background = RippleDrawable(ColorStateList.valueOf(0x145553E8), roundRect(activity, Color.WHITE, 0xFFE4E7F0.toInt()), null)
            elevation = dp(activity, 2).toFloat()
            isClickable = true; isFocusable = true
            setOnClickListener { action() }
            pressMotion(this)
        }
        val badge = TextView(activity).apply {
            text = icon; textSize = 22f; gravity = Gravity.CENTER; setTextColor(badgeInks[colorIndex])
            background = roundRect(activity, badgeColors[colorIndex], null)
        }
        tile.addView(badge, LinearLayout.LayoutParams(dp(activity, 44), dp(activity, 44)))
        tile.addView(View(activity), LinearLayout.LayoutParams(1, dp(activity, 8)))
        tile.addView(text(activity, title, 15, INK, true).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        tile.addView(text(activity, detail, 11, MUTED).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
        val widthPx = activity.resources.displayMetrics.widthPixels
        val tileSide = ((widthPx - 2 * dp(activity, 18) - dp(activity, 20)) / 2).coerceAtLeast(dp(activity, 128))
        parent.addView(tile, LinearLayout.LayoutParams(0, tileSide, 1f).apply {
            leftMargin = dp(activity, 5); rightMargin = dp(activity, 5); bottomMargin = dp(activity, 10)
        })
    }

    fun statusBar(activity: AppCompatActivity, parent: LinearLayout): TextView = TextView(activity).apply {
        visibility = View.GONE; textSize = 12f; setTypeface(typeface, Typeface.BOLD); gravity = Gravity.TOP or Gravity.START
        maxHeight = dp(activity, 190); isVerticalScrollBarEnabled = true; android.text.method.ScrollingMovementMethod.getInstance().also { movementMethod = it }
        setPadding(dp(activity, 13), dp(activity, 9), dp(activity, 13), dp(activity, 9))
        parent.addView(this, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(activity, 8) })
    }

    fun showError(activity: AppCompatActivity, bar: TextView, message: String) {
        bar.text = "⚠   $message"; bar.setTextColor(ERROR)
        bar.background = statusBackground(activity, 0xFFFDECEA.toInt(), 0xFFF5B9B2.toInt())
        showStatus(bar)
    }

    fun showSuccess(activity: AppCompatActivity, bar: TextView, message: String) {
        bar.text = "✓   $message"; bar.setTextColor(SUCCESS)
        bar.background = statusBackground(activity, 0xFFE8F5EE.toInt(), 0xFFB7E1C8.toInt())
        showStatus(bar)
    }

    fun clearStatus(bar: TextView) { bar.visibility = View.GONE; bar.text = "" }

    private fun showStatus(bar: TextView) {
        bar.visibility = View.VISIBLE
        bar.alpha = 0f; bar.translationY = dp(bar.context as AppCompatActivity, 5).toFloat()
        bar.animate().alpha(1f).translationY(0f).setDuration(180).start()
    }

    fun surface(activity: AppCompatActivity, dark: Boolean = false): GradientDrawable =
        roundRect(activity, if (dark) 0xFF171D2E.toInt() else Color.WHITE, if (dark) 0xFF30384F.toInt() else 0xFFE7E9F1.toInt())

    fun spacer(activity: AppCompatActivity, parent: LinearLayout, height: Int = 10) {
        parent.addView(View(activity), LinearLayout.LayoutParams(1, dp(activity, height)))
    }

    private fun statusBackground(activity: AppCompatActivity, fill: Int, stroke: Int) = GradientDrawable().apply {
        cornerRadius = dp(activity, 13).toFloat(); setColor(fill); setStroke(dp(activity, 1), stroke)
    }

    private fun roundRect(activity: AppCompatActivity, fill: Int, stroke: Int?): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(activity, 17).toFloat(); setColor(fill)
        stroke?.let { setStroke(dp(activity, 1), it) }
    }

    private fun pressMotion(view: View) {
        view.setOnTouchListener { target, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> target.animate().scaleX(.985f).scaleY(.985f).setDuration(90).start()
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> target.animate().scaleX(1f).scaleY(1f).setDuration(130).start()
            }
            false
        }
    }

    private fun Int.absoluteValueSafe(): Int = if (this == Int.MIN_VALUE) 0 else kotlin.math.abs(this)
}
