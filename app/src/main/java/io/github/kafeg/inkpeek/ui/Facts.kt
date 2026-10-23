package io.github.kafeg.inkpeek.ui

import android.content.Context
import io.github.kafeg.inkpeek.R

/** The small facts under the card's header: how common a word is, how often it was looked up here. */
object Facts {
    /** The frequency band of a word's [rank] (1: the most frequent): top 1,000 / 5,000 / 20,000, else nothing. */
    fun band(rank: Int): Int? = listOf(1000, 5000, 20000).firstOrNull { rank in 1..it }

    fun frequency(ctx: Context, rank: Int): String? = band(rank)?.let {
        ctx.getString(R.string.freq_top, java.text.NumberFormat.getIntegerInstance(ctx.resources.configuration.locales[0]).format(it)) }

    /** "Looked up 3 times" from the second look on. */
    fun looks(ctx: Context, n: Int): String? = if (n < 2) null else ctx.resources.getQuantityString(R.plurals.looks, n, n)
}
