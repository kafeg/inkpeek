package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.stardict.StarDict
import io.github.kafeg.inkpeek.stardict.answer
import io.github.kafeg.inkpeek.stardict.book
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The card's dictionaries in one list, packs and others together, ↑ ↓ to order them (Card → Main). */
class OrderActivity : Activity(), CoroutineScope by MainScope() {
    private lateinit var prefs: Prefs
    private lateinit var list: LinearLayout

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Ink.localized(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        Ink.theme(this)
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = Ink.dp(this, 16)
        val col = Ink.vertical(this).apply { setPadding(pad, pad, pad, pad) }
        col.addView(Rows.header(this, getString(R.string.order_t)))
        col.addView(Rows.note(this, getString(R.string.order_note)))
        list = Ink.vertical(this); col.addView(list)
        setContentView(ScrollView(this).apply { addView(col) })
        refresh()
    }

    override fun onDestroy() { cancel(); super.onDestroy() }

    private var shown: List<StarDict> = emptyList()

    private fun refresh() = launch {
        val lib = prefs.library()
        shown = withContext(Dispatchers.IO) { lib.all() }
        draw()
    }

    private fun draw() {
        val lib = prefs.library(); val dicts = shown
        list.removeAllViews()
        dicts.forEachIndexed { i, d ->
            val r = Ink.horizontal(this).apply { gravity = android.view.Gravity.CENTER_VERTICAL; isBaselineAligned = false }
            r.addView(Rows.texts(this, title(d, lib.isPack(d)), if (lib.isPack(d)) getString(R.string.order_pack) else
                (d.ifo.parentFile?.parentFile?.let { short(it) } ?: ""), 1), Ink.lp(0, weight = 1f))
            val side = Ink.dp(this, 44)
            r.addView(Ink.icon(this, R.drawable.ic_up, getString(R.string.up), 44) { move(i, -1) }.apply { alpha = if (i > 0) 1f else Ink.OFF },
                LinearLayout.LayoutParams(side, side).apply { leftMargin = Ink.dp(this@OrderActivity, 8) })
            r.addView(Ink.icon(this, R.drawable.ic_down, getString(R.string.down), 44) { move(i, 1) }.apply { alpha = if (i < dicts.size - 1) 1f else Ink.OFF },
                LinearLayout.LayoutParams(side, side).apply { leftMargin = Ink.dp(this@OrderActivity, 6) })
            list.addView(r, Ink.lp().apply { topMargin = Ink.dp(this@OrderActivity, 12) })
        }
    }

    /** One step in the list as shown (at once: quick taps each move from the last one); others keep their place after it. */
    private fun move(i: Int, by: Int) {
        val j = i + by; if (i !in shown.indices || j !in shown.indices) return
        shown = shown.toMutableList().apply { this[i] = this[j].also { this[j] = this[i] } }
        val ids = shown.map { it.id }
        prefs.order = ids + prefs.order.filter { it !in ids }
        draw()
    }

    private fun title(d: StarDict, pack: Boolean): String {
        if (!pack) return d.name
        fun name(c: String?) = c?.let { Ink.lang(this, it) }
        return "InkPeek · " + listOfNotNull(name(d.book), name(d.answer)).joinToString(" → ")
    }

    private fun short(f: java.io.File) = f.path.removePrefix(android.os.Environment.getExternalStorageDirectory().path + "/")
}
