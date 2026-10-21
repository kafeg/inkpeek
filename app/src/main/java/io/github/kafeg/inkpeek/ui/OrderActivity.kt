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

    private fun refresh() = launch {
        val lib = prefs.library()
        val dicts = withContext(Dispatchers.IO) { lib.all() }
        list.removeAllViews()
        dicts.forEachIndexed { i, d ->
            val r = Ink.horizontal(this@OrderActivity).apply { gravity = android.view.Gravity.CENTER_VERTICAL; isBaselineAligned = false }
            r.addView(Rows.texts(this@OrderActivity, title(d, lib.isPack(d)), if (lib.isPack(d)) getString(R.string.order_pack) else
                (d.ifo.parentFile?.parentFile?.let { short(it) } ?: ""), 1), Ink.lp(0, weight = 1f))
            val side = Ink.dp(this@OrderActivity, 44)
            r.addView(Ink.icon(this@OrderActivity, R.drawable.ic_up, getString(R.string.up), 44) { move(dicts, i, -1) }.apply { alpha = if (i > 0) 1f else 0.3f },
                LinearLayout.LayoutParams(side, side).apply { leftMargin = Ink.dp(this@OrderActivity, 8) })
            r.addView(Ink.icon(this@OrderActivity, R.drawable.ic_down, getString(R.string.down), 44) { move(dicts, i, 1) }.apply { alpha = if (i < dicts.size - 1) 1f else 0.3f },
                LinearLayout.LayoutParams(side, side).apply { leftMargin = Ink.dp(this@OrderActivity, 6) })
            list.addView(r, Ink.lp().apply { topMargin = Ink.dp(this@OrderActivity, 12) })
        }
    }

    /** The whole list as it is shown, with one step; ids of dictionaries not in use now keep their place after it. */
    private fun move(dicts: List<StarDict>, i: Int, by: Int) {
        val j = i + by; if (j !in dicts.indices) return
        val ids = dicts.map { it.id }.toMutableList(); ids[i] = ids[j].also { ids[j] = ids[i] }
        prefs.order = ids + prefs.order.filter { it !in ids }
        refresh()
    }

    private fun title(d: StarDict, pack: Boolean): String {
        if (!pack) return d.name
        fun name(c: String?) = c?.let { java.util.Locale(it).getDisplayLanguage(resources.configuration.locales[0]).replaceFirstChar { ch -> ch.uppercase() } }
        return "InkPeek · " + listOfNotNull(name(d.book), name(d.answer)).joinToString(" → ")
    }

    private fun short(f: java.io.File) = f.path.removePrefix(android.os.Environment.getExternalStorageDirectory().path + "/")
}
