package io.github.kafeg.inkpeek.ui

import android.content.Context
import android.view.Gravity
import io.github.kafeg.inkpeek.Store

/** Notes, in the app and in the card: one row per entry — the text and one line, tap opens it, ✕ removes it. */
object WordsList {
    fun row(ctx: Context, e: Store.Entry, sp: Float, open: () -> Unit, remove: () -> Unit) = Ink.vertical(ctx).apply {
        addView(line(ctx, e, sp, open, remove)); addView(Ink.divider(ctx))
    }

    private fun line(ctx: Context, e: Store.Entry, sp: Float, open: () -> Unit, remove: () -> Unit) = Ink.horizontal(ctx).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(Ink.entry(ctx, e.text, e.meaning, sp).apply {
            val q = Ink.dp(ctx, 6); setPadding(0, q, q, q); setOnClickListener { open() }
            // The text and its line: each selectable as a whole when held, a tap still opens the entry.
            (0 until childCount).mapNotNull { getChildAt(it) as? android.widget.TextView }.forEach { tv ->
                TextActions.selectable(tv) { it.length }; tv.setOnClickListener { open() } }
        }, Ink.lp(0, weight = 1f))
        addView(Ink.button(ctx, "✕", sp * 0.9f) { remove() })
    }

    const val PAGE = 50
    /** A list in the card's dictionary tab: fewer at a time, each with a meaning. */
    const val PAGE_SHORT = 20

    /** ◀ "51–100 of 230" ▶ under a long list; [turn] gets the new page. */
    fun pager(ctx: Context, page: Int, total: Int, sp: Float, size: Int = PAGE, turn: (Int) -> Unit) = Ink.horizontal(ctx).apply {
        gravity = Gravity.CENTER_VERTICAL; isBaselineAligned = false
        val pages = (total + size - 1) / size; val side = Ink.dp(ctx, 48)
        addView(Ink.icon(ctx, io.github.kafeg.inkpeek.R.drawable.ic_prev, ctx.getString(io.github.kafeg.inkpeek.R.string.prev)) { if (page > 0) turn(page - 1) }
            .apply { alpha = if (page > 0) 1f else Ink.OFF }, android.widget.LinearLayout.LayoutParams(side, side))
        addView(Ink.text(ctx, ctx.getString(io.github.kafeg.inkpeek.R.string.page_of, page * size + 1, minOf(total, (page + 1) * size), total), sp)
            .apply { gravity = Gravity.CENTER }, Ink.lp(android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = Ink.dp(ctx, 10); rightMargin = Ink.dp(ctx, 10) })
        addView(Ink.icon(ctx, io.github.kafeg.inkpeek.R.drawable.ic_next, ctx.getString(io.github.kafeg.inkpeek.R.string.next)) { if (page < pages - 1) turn(page + 1) }
            .apply { alpha = if (page < pages - 1) 1f else Ink.OFF }, android.widget.LinearLayout.LayoutParams(side, side))
    }

    /** The card for [text]. */
    fun card(ctx: Context, text: String) = android.content.Intent(ctx, CardActivity::class.java)
        .setAction("colordict.intent.action.SEARCH").putExtra("EXTRA_QUERY", text)
}
