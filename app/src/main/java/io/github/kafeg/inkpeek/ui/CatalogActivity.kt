package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.stardict.Catalog
import io.github.kafeg.inkpeek.stardict.CatalogItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Dictionaries of the switched-on public sources: pairs into the app's language first, WikDict first among them. */
class CatalogActivity : Activity(), CoroutineScope by MainScope() {
    private lateinit var prefs: Prefs
    private var items: List<CatalogItem> = emptyList()
    private var shown: List<CatalogItem> = emptyList()
    private lateinit var list: ListView
    private lateinit var info: TextView
    private lateinit var filter: EditText
    private val dlg = Rows.JobDialog(this)
    /** The names of the dictionaries in the folder now: a source's ✓ only while one it brought is there. */
    private var present: Set<String> = emptySet()

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Ink.localized(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        Ink.theme(this)
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = Ink.dp(this, 16)
        val col = Ink.vertical(this).apply { setPadding(pad, pad, pad, pad) }
        info = Ink.text(this, getString(R.string.loading_lists), 16f)
        filter = EditText(this).apply { hint = getString(R.string.catalog_filter); textSize = 18f; setSingleLine() }
        list = ListView(this)
        col.addView(Rows.header(this, getString(R.string.lang_own)))
        col.addView(filter); col.addView(Ink.text(this, getString(R.string.cat_recommended), 14f)); col.addView(info)
        col.addView(list, Ink.lp(h = 0, weight = 1f))
        setContentView(col)
        filter.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = show()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        list.setOnItemClickListener { _, _, i, _ -> confirm(shown[i]) }
        launch {
            val sources = Catalog.SOURCES.filter { it.id !in prefs.sourcesOff }
            val loaded = withContext(Dispatchers.IO) { sources.map { s -> s to runCatching { s.parse(get(s.url)) } } }
            val failed = loaded.filter { it.second.isFailure }.map { it.first.title }
            if (failed.isNotEmpty()) toast(getString(R.string.failed, failed.joinToString()))
            val rank = Catalog.SOURCES.map { it.id }
            items = loaded.flatMap { it.second.getOrDefault(emptyList()) }
                .sortedWith(compareBy({ it.to != answer }, { !recommended(it) }, { name(it.from) }, { rank.indexOf(it.source) }))
            marks()
        }
    }

    override fun onResume() {
        super.onResume()
        // Progress goes to the dialog; the list (✓ marks) is redrawn once, at the end.
        Downloads.listener = { if (!isDestroyed) { dlg.update(); if (!Downloads.busy) marks() } }
        if (Downloads.busy) dlg.show() else dlg.update()
    }
    override fun onPause() { Downloads.listener = null; super.onPause() }
    override fun onDestroy() { dlg.dismiss(); cancel(); super.onDestroy() }

    private val answer get() = prefs.answerCode
    private fun recommended(i: CatalogItem) = i.source == "wikdict" && i.to == answer
    private fun title(i: CatalogItem) = Catalog.SOURCES.first { it.id == i.source }.title
    private fun name(code: String) = Ink.lang(this, code)

    private fun show() {
        val f = filter.text.toString().trim().lowercase()
        shown = items.filter { f.isEmpty() || "${it.from}-${it.to} ${title(it)} ${name(it.from)} ${name(it.to)}".lowercase().contains(f) }
        info.text = getString(R.string.n_of_m, shown.size, items.size)
        val got = prefs.got.map { it.split('\t') }.filter { e -> e.drop(1).any { it in present } }.map { it.first() }.toSet()
        list.adapter = Rows.adapter(this, shown.map { (if (recommended(it)) "★ " else "") + "${name(it.from)} → ${name(it.to)}" to
            (listOf(title(it), mb(it.size)) + if (it.url in got) "✓ " + getString(R.string.got_mark) else "").filter { s -> s.isNotEmpty() }.joinToString(" · ") })
    }

    /** Reads which dictionaries are in the folder, then redraws the list. */
    private fun marks() = launch {
        present = withContext(Dispatchers.IO) { runCatching { prefs.library().found().map { it.name }.toSet() }.getOrDefault(emptySet()) }
        show()
    }

    private fun mb(b: Long) = if (b <= 0) "" else getString(R.string.size_mb, "%.1f".format(b / 1e6))

    private fun get(url: String): String = io.github.kafeg.inkpeek.Net.text(url)

    /** One download at a time: while one runs, a tap shows its progress instead. */
    private fun confirm(item: CatalogItem) {
        if (Downloads.busy) { dlg.show(); return }
        InkDialog(this).title("${title(item)}: ${name(item.from)} → ${name(item.to)}")
            .message(getString(when (item.source) { "wikdict" -> R.string.cat_wikdict; "wiktionary" -> R.string.cat_wiktionary; else -> R.string.cat_freedict }) +
                "\n\n${mb(item.size)} · ${item.license}" + (if (prefs.got.any { it.startsWith(item.url + "\t") && it.split('\t').drop(1).any { n -> n in present } }) "\n✓ " + getString(R.string.got_mark) else ""))
            .button(R.string.cancel).button(R.string.download) { Downloads.dict(this, item, "${name(item.from)} → ${name(item.to)}"); dlg.show() }.show()
    }

    private fun toast(s: String) = Ink.toast(this, s)
}
