package io.github.kafeg.inkpeek.ui

import android.content.Context
import android.view.Gravity
import io.github.kafeg.inkpeek.Store

/** Notes, in the app and in the card: one row per entry — the text and one line, tap opens it, ✕ removes it. */
object WordsList {
    fun row(ctx: Context, e: Store.Entry, sp: Float, open: () -> Unit, remove: () -> Unit) = Ink.vertical(ctx).apply {
        addView(line(ctx, e, sp, open, remove)); addView(Ink.divider(ctx))
    }

    private fun line(ctx: Context, e: Store.Entry, sp: Float, open: () -> Unit, remove: () -> Unit) = Ink.opener(ctx,
        Ink.entry(ctx, e.text, e.meaning, sp).apply {
            val q = Ink.dp(ctx, 6); setPadding(0, q, q, q)
            (0 until childCount).mapNotNull { getChildAt(it) as? android.widget.TextView }.forEach { TextActions.selectable(it) }
        }, open, Ink.button(ctx, "✕", sp * 0.9f) { remove() })

    const val PAGE = 50
    /** A list in the card's dictionary tab: fewer at a time, each with a meaning. */
    const val PAGE_SHORT = 20

    /** ◀ "51–100 of 230" ▶ over a long list; [turn] gets the new page. */
    fun pager(ctx: Context, page: Int, total: Int, sp: Float, size: Int = PAGE, turn: (Int) -> Unit) =
        bar(ctx, Ink.escape(ctx.getString(io.github.kafeg.inkpeek.R.string.page_of, page * size + 1, minOf(total, (page + 1) * size), total)),
            sp, page, (total + size - 1) / size, turn)

    /**
     * The one page bar (lists, sections, the dictionaries' full articles): ◀ [html] ▶, the width of the page, bold in the
     * middle; the arrows only with more than one page ([at] of [count]), dimmed at the ends; [turn] gets the new one.
     */
    fun bar(ctx: Context, html: String, sp: Float, at: Int, count: Int, turn: (Int) -> Unit) = Ink.horizontal(ctx).apply {
        gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false
        val side = Ink.dp(ctx, 40); val many = count > 1
        if (many) addView(Ink.icon(ctx, io.github.kafeg.inkpeek.R.drawable.ic_prev, ctx.getString(io.github.kafeg.inkpeek.R.string.prev), 40) { if (at > 0) turn(at - 1) }
            .apply { alpha = if (at > 0) 1f else Ink.OFF }, android.widget.LinearLayout.LayoutParams(side, side))
        addView(Ink.text(ctx, html, sp, bold = true, html = true).apply { gravity = Gravity.CENTER; maxLines = 2 },
            Ink.lp(0, weight = 1f).apply { leftMargin = Ink.dp(ctx, 8); rightMargin = Ink.dp(ctx, 8) })
        if (many) addView(Ink.icon(ctx, io.github.kafeg.inkpeek.R.drawable.ic_next, ctx.getString(io.github.kafeg.inkpeek.R.string.next), 40) { if (at < count - 1) turn(at + 1) }
            .apply { alpha = if (at < count - 1) 1f else Ink.OFF }, android.widget.LinearLayout.LayoutParams(side, side))
    }

    /** The card for [text]. */
    fun card(ctx: Context, text: String) = android.content.Intent(ctx, CardActivity::class.java)
        .setAction("colordict.intent.action.SEARCH").putExtra("EXTRA_QUERY", text)
}
