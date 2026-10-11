package io.github.kafeg.inkpeek.ui

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.Html
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

/** E-ink styling: black on white, thin borders, no ripples or colors. */
object Ink {
    fun dp(ctx: Context, v: Number) = (v.toFloat() * ctx.resources.displayMetrics.density).toInt()

    fun border(ctx: Context, width: Int = 2, radius: Int = 6, fill: Int = Color.WHITE) = GradientDrawable().apply {
        setColor(fill); setStroke(dp(ctx, width) / 2 + 1, Color.BLACK); cornerRadius = dp(ctx, radius).toFloat()
    }

    fun text(ctx: Context, s: CharSequence, sp: Float, bold: Boolean = false, html: Boolean = false) = TextView(ctx).apply {
        text = if (html) Html.fromHtml(s.toString(), Html.FROM_HTML_MODE_COMPACT) else s
        setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.15f); setTextIsSelectable(false)
    }

    fun button(ctx: Context, label: String, sp: Float, onClick: (View) -> Unit) = TextView(ctx).apply {
        text = label; setTextColor(Color.BLACK); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); gravity = Gravity.CENTER
        background = border(ctx); val p = dp(ctx, 10); setPadding(p, p, p, p); setOnClickListener(onClick)
        isClickable = true; isFocusable = true
    }

    fun label(ctx: Context, a: io.github.kafeg.inkpeek.ai.Action) = res(ctx, "a_${a.id}", a.id)
    fun description(ctx: Context, a: io.github.kafeg.inkpeek.ai.Action) = res(ctx, "a_${a.id}_d", "")
    private fun res(ctx: Context, name: String, fallback: String) =
        ctx.resources.getIdentifier(name, "string", ctx.packageName).takeIf { it != 0 }?.let { ctx.getString(it) } ?: fallback

    /** The little Markdown the AI may use (**bold**, *italic*, "- " lists) -> HTML. */
    fun markdown(s: String): String = s.trim().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>").replace(Regex("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![*\\w])"), "<i>$1</i>")
        .replace(Regex("(?m)^#+\\s*"), "").replace(Regex("(?m)^\\s*[-•]\\s+"), "• ").replace("\n", "<br>")

    fun vertical(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    fun horizontal(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight)
}
