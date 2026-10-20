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
        }, Ink.lp(0, weight = 1f))
        addView(Ink.button(ctx, "✕", sp * 0.9f) { remove() })
    }

    /** The card for [text]. */
    fun card(ctx: Context, text: String) = android.content.Intent(ctx, CardActivity::class.java)
        .setAction("colordict.intent.action.SEARCH").putExtra("EXTRA_QUERY", text)
}
