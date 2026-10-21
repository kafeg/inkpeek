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

/** E-ink styling: black on white (or white on black: the dark card), thin borders, no ripples or colors. */
object Ink {
    /** Ink and paper of a screen: the card can be dark (Card → Look), the settings never are. */
    fun fg(ctx: Context) = if ((ctx as? CardActivity)?.dark == true) Color.WHITE else Color.BLACK
    fun bg(ctx: Context) = if ((ctx as? CardActivity)?.dark == true) Color.BLACK else Color.WHITE

    fun dp(ctx: Context, v: Number) = (v.toFloat() * ctx.resources.displayMetrics.density).toInt()

    fun border(ctx: Context, width: Int = 2, radius: Int = 6) = GradientDrawable().apply {
        setColor(bg(ctx)); setStroke(dp(ctx, width) / 2 + 1, fg(ctx)); cornerRadius = dp(ctx, radius).toFloat()
    }

    fun text(ctx: Context, s: CharSequence, sp: Float, bold: Boolean = false, html: Boolean = false) = TextView(ctx).apply {
        text = if (html) Html.fromHtml(s.toString(), Html.FROM_HTML_MODE_COMPACT) else s
        setTextColor(fg(ctx)); setLinkTextColor(fg(ctx)); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        if (bold) typeface = Typeface.DEFAULT_BOLD
        setLineSpacing(0f, 1.15f); setTextIsSelectable(false)
    }

    fun button(ctx: Context, label: String, sp: Float, onClick: (View) -> Unit) = TextView(ctx).apply {
        text = label; setTextColor(fg(ctx)); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); gravity = Gravity.CENTER
        background = border(ctx); val p = dp(ctx, 10); setPadding(p, p, p, p); setOnClickListener(onClick)
        isClickable = true; isFocusable = true
    }

    /** A tab button: one line, the text shrinks to fit a narrow screen (a phone) instead of breaking the word. */
    fun tab(ctx: Context, label: String, sp: Float, onClick: (View) -> Unit) = button(ctx, label, sp, onClick).apply {
        maxLines = 1 // the padding stays clear of the open tab's thick border; the text shrinks instead
        setAutoSizeTextTypeUniformWithConfiguration(10, sp.toInt(), 1, TypedValue.COMPLEX_UNIT_SP)
    }

    /** Second-level tabs: plain text, the open one bold over a thick line, so they differ from bordered buttons. */
    fun subTabs(ctx: Context, items: List<Pair<String, String>>, open: String, sp: Float, pick: (String) -> Unit) = horizontal(ctx).apply {
        items.forEach { (k, label) ->
            val on = k == open
            addView(vertical(ctx).apply {
                tag = k; isClickable = true; setOnClickListener { pick(k) }
                addView(text(ctx, label, sp, bold = on).apply { gravity = Gravity.CENTER; val p = dp(ctx, 8); setPadding(0, p, 0, p) })
                addView(View(ctx).apply { setBackgroundColor(fg(ctx)) }, lp(h = dp(ctx, if (on) 4 else 1)))
            }, lp(0, weight = 1f))
        }
    }

    /** A list row: [title] in bold, one line of [meaning] under it ("—" when none); both cut with "…", rows keep their height. */
    fun entry(ctx: Context, title: String, meaning: String, sp: Float) = vertical(ctx).apply {
        addView(text(ctx, title, sp, bold = true).apply { isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END })
        addView(text(ctx, meaning.ifBlank { "—" }, sp * 0.85f).apply { isSingleLine = true; ellipsize = android.text.TextUtils.TruncateAt.END })
    }

    fun label(ctx: Context, a: io.github.kafeg.inkpeek.ai.Action) = res(ctx, "a_${a.id}", a.id)
    fun description(ctx: Context, a: io.github.kafeg.inkpeek.ai.Action) = res(ctx, "a_${a.id}_d", "")
    private fun res(ctx: Context, name: String, fallback: String) =
        ctx.resources.getIdentifier(name, "string", ctx.packageName).takeIf { it != 0 }?.let { ctx.getString(it) } ?: fallback

    fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    /** The little Markdown the AI may use (**bold**, *italic*, "- " lists) -> HTML. */
    fun markdown(s: String): String = s.trim().replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        .replace(Regex("\\*\\*(.+?)\\*\\*"), "<b>$1</b>").replace(Regex("(?<![*\\w])\\*(?!\\s)(.+?)(?<!\\s)\\*(?![*\\w])"), "<i>$1</i>")
        .replace(Regex("(?m)^#+\\s*"), "").replace(Regex("(?m)^\\s*[-•]\\s+"), "• ").replace("\n", "<br>")

    /** [base] with the app language from settings applied (activities call it in attachBaseContext). */
    fun localized(base: Context): Context {
        val lang = base.getSharedPreferences("inkpeek", Context.MODE_PRIVATE).getString("ui_lang", "").orEmpty()
        if (lang.isEmpty()) return base
        val conf = android.content.res.Configuration(base.resources.configuration).apply { setLocale(java.util.Locale(lang)) }
        return base.createConfigurationContext(conf)
    }

    fun vertical(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL }
    fun horizontal(ctx: Context) = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL }
    fun lp(w: Int = ViewGroup.LayoutParams.MATCH_PARENT, h: Int = ViewGroup.LayoutParams.WRAP_CONTENT, weight: Float = 0f) =
        LinearLayout.LayoutParams(w, h, weight)
}
