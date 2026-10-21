package io.github.kafeg.inkpeek.ui

import android.content.Context
import android.view.Gravity
import io.github.kafeg.inkpeek.Store

/** My words, in the app and in the card: one row per entry — the text and one line, tap opens it, ✕ removes it. */
object WordsList {
    fun row(ctx: Context, e: Store.Entry, sp: Float, open: () -> Unit, remove: () -> Unit) = Ink.horizontal(ctx).apply {
        gravity = Gravity.CENTER_VERTICAL
        addView(Ink.entry(ctx, e.text, e.meaning, sp).apply {
            val q = Ink.dp(ctx, 6); setPadding(0, q, q, q); setOnClickListener { open() }
            // The text and its line: each selectable as a whole when held, a tap still opens the entry.
            (0 until childCount).mapNotNull { getChildAt(it) as? android.widget.TextView }.forEach { tv ->
                TextActions.selectable(tv) { it.length }; tv.setOnClickListener { open() } }
        }, Ink.lp(0, weight = 1f))
        addView(Ink.button(ctx, "✕", sp * 0.9f) { remove() })
    }

    /** The card for [text]. */
    fun card(ctx: Context, text: String) = android.content.Intent(ctx, CardActivity::class.java)
        .setAction("colordict.intent.action.SEARCH").putExtra("EXTRA_QUERY", text)
}
