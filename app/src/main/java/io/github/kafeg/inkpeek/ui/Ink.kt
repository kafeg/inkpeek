package io.github.kafeg.inkpeek.ui

import android.content.Context
import android.content.res.Configuration
import io.github.kafeg.inkpeek.Prefs
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

/** E-ink styling: black on white (or white on black: the dark theme), thin borders, no ripples or colors. */
object Ink {
    /** White on black (Card → Main → Theme: dark, or the system's night mode), for the whole app. */
    fun dark(ctx: Context) = ctx !is Paper && when (Prefs(ctx).theme) {
        "dark" -> true
        "system" -> ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
        else -> false
    }
    /** A control that does nothing now (the first page's ◀): dimmed, still seen on e-ink. */
    const val OFF = 0.45f

    fun fg(ctx: Context) = if (dark(ctx)) Color.WHITE else Color.BLACK
    fun bg(ctx: Context) = if (dark(ctx)) Color.BLACK else Color.WHITE

    /** Black on white whatever the theme: dialog content (Boox draws every dialog's frame white, ignoring the app's theme). */
    class Paper(base: Context) : android.view.ContextThemeWrapper(base, io.github.kafeg.inkpeek.R.style.Theme_InkPeek)
    fun paper(ctx: Context) = Paper(ctx)

    /** A screen in the theme: called before its onCreate. */
    fun theme(a: android.app.Activity, card: Boolean = false) = a.setTheme(when {
        card -> if (dark(a)) io.github.kafeg.inkpeek.R.style.Theme_InkPeek_Card_Dark else io.github.kafeg.inkpeek.R.style.Theme_InkPeek_Card
        else -> if (dark(a)) io.github.kafeg.inkpeek.R.style.Theme_InkPeek_Dark else io.github.kafeg.inkpeek.R.style.Theme_InkPeek
    })

    /** A language's name in the app's language, capitalized: "Немецкий"; the code when Android has no name for it. */
    fun lang(ctx: Context, code: String): String = java.util.Locale(code).getDisplayLanguage(ctx.resources.configuration.locales[0])
        .takeIf { it.isNotBlank() && it != code }?.replaceFirstChar { it.uppercase() } ?: code

    fun dp(ctx: Context, v: Number) = (v.toFloat() * ctx.resources.displayMetrics.density).toInt()

    fun border(ctx: Context, width: Int = 2, radius: Int = 6) = GradientDrawable().apply {
        setColor(bg(ctx)); setStroke(dp(ctx, width) / 2 + 1, fg(ctx)); cornerRadius = dp(ctx, radius).toFloat()
    }

    fun text(ctx: Context, s: CharSequence, sp: Float, bold: Boolean = false, html: Boolean = false) = TextView(ctx).apply {
        setTextColor(fg(ctx)); setLinkTextColor(fg(ctx)); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        text = arrows(ctx, if (html) plain(Html.fromHtml(s.toString(), Html.FROM_HTML_MODE_COMPACT)) else s, textSize)
        // The card's text in the font chosen for it (Card → Appearance); the settings in the system's.
        val face = if (ctx is CardActivity) Fonts.face(ctx.prefs.cardFont) else null
        if (bold) typeface = Typeface.create(face ?: Typeface.DEFAULT, Typeface.BOLD) else if (face != null) typeface = face
        setLineSpacing(0f, 1.15f); setTextIsSelectable(false)
    }

    /** A dictionary's own colors (WikDict's green grammar, grey IPA) taken out: e-ink is black and white. */
    private fun plain(t: CharSequence): CharSequence {
        val out = t as? android.text.Spannable ?: android.text.SpannableString(t)
        listOf(android.text.style.ForegroundColorSpan::class.java, android.text.style.BackgroundColorSpan::class.java)
            .flatMap { out.getSpans(0, out.length, it).toList() }.forEach { out.removeSpan(it) }
        return out
    }

    /** "→" as a drawn arrow in the middle of the line (the font's glyph sits low), as big as the text around it. */
    fun arrows(ctx: Context, s: CharSequence, px: Float): CharSequence {
        if (!s.contains('→')) return s
        val out = android.text.SpannableStringBuilder(s)
        s.indices.filter { s[it] == '→' }.forEach { i ->
            // <small> and other relative sizes at that place: the arrow of a small caption is small too.
            val scale = out.getSpans(i, i + 1, android.text.style.RelativeSizeSpan::class.java).fold(1f) { k, sp -> k * sp.sizeChange }
            val side = (px * scale * 0.9f).toInt()
            val d = ctx.getDrawable(io.github.kafeg.inkpeek.R.drawable.ic_forward)!!.mutate().apply { setTint(fg(ctx)); setBounds(0, 0, side, side) }
            out.setSpan(android.text.style.ImageSpan(d, android.text.style.DynamicDrawableSpan.ALIGN_CENTER), i, i + 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    /** A bordered icon button, as in the card's bar. */
    fun icon(ctx: Context, res: Int, label: String, sideDp: Int = 48, onClick: (View) -> Unit) = android.widget.ImageView(ctx).apply {
        setImageResource(res); setColorFilter(fg(ctx)); background = border(ctx); val q = dp(ctx, sideDp) / 5; setPadding(q, q, q, q)
        contentDescription = label; tooltipText = label; isClickable = true; setOnClickListener(onClick)
        layoutParams = ViewGroup.LayoutParams(dp(ctx, sideDp), dp(ctx, sideDp))
    }

    fun button(ctx: Context, label: String, sp: Float, onClick: (View) -> Unit) = TextView(ctx).apply {
        text = label; setTextColor(fg(ctx)); setTextSize(TypedValue.COMPLEX_UNIT_SP, sp); gravity = Gravity.CENTER
        background = border(ctx); val p = dp(ctx, 10); setPadding(p, p, p, p); setOnClickListener(onClick)
        isClickable = true; isFocusable = true
    }

    /** A tab button: one line, the text shrinks to fit a narrow screen (a phone) instead of breaking the word. */
    fun tab(ctx: Context, label: String, sp: Float, onClick: (View) -> Unit) = button(ctx, label, sp, onClick).apply {
        maxLines = 1; gravity = Gravity.CENTER // the padding stays clear of the open tab's thick border; the text shrinks instead
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
