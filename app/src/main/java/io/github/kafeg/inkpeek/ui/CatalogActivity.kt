package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.stardict.Catalog
import io.github.kafeg.inkpeek.stardict.CatalogItem
import io.github.kafeg.inkpeek.stardict.Importer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

/** Dictionaries of the switched-on public sources: pairs into the app's language first, WikDict first among them. */
class CatalogActivity : Activity(), CoroutineScope by MainScope() {
    private val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
    private lateinit var prefs: Prefs
    private var items: List<CatalogItem> = emptyList()
    private var shown: List<CatalogItem> = emptyList()
    private lateinit var list: ListView
    private lateinit var info: TextView
    private lateinit var filter: EditText

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Ink.localized(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = Ink.dp(this, 16)
        val col = Ink.vertical(this).apply { setPadding(pad, pad, pad, pad) }
        info = Ink.text(this, getString(R.string.loading_lists), 16f)
        filter = EditText(this).apply { hint = getString(R.string.catalog_filter); textSize = 18f; setSingleLine() }
        list = ListView(this)
        col.addView(Ink.text(this, getString(R.string.lang_own), 24f, bold = true))
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
            show()
        }
    }

    override fun onDestroy() { cancel(); super.onDestroy() }

    private val answer get() = prefs.answerCode
    private fun recommended(i: CatalogItem) = i.source == "wikdict" && i.to == answer
    private fun title(i: CatalogItem) = Catalog.SOURCES.first { it.id == i.source }.title
    private fun name(code: String) = java.util.Locale(code).getDisplayLanguage(resources.configuration.locales[0])
        .takeIf { it.isNotBlank() && it != code }?.replaceFirstChar { it.uppercase() } ?: code

    private fun show() {
        val f = filter.text.toString().trim().lowercase()
        shown = items.filter { f.isEmpty() || "${it.from}-${it.to} ${title(it)} ${name(it.from)} ${name(it.to)}".lowercase().contains(f) }
        info.text = getString(R.string.n_of_m, shown.size, items.size)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            shown.map { (if (recommended(it)) "★ " else "") + "${name(it.from)} → ${name(it.to)}   ${title(it)}   ${mb(it.size)}" })
    }

    private fun mb(b: Long) = if (b <= 0) "" else "%.1f MB".format(b / 1e6)

    private fun get(url: String): String = http.newCall(Request.Builder().url(url).build()).execute().use {
        if (!it.isSuccessful) error("HTTP ${it.code}"); it.body!!.string()
    }

    private fun confirm(item: CatalogItem) = AlertDialog.Builder(this).setTitle("${title(item)}: ${name(item.from)} → ${name(item.to)}")
        .setMessage(getString(when (item.source) { "wikdict" -> R.string.cat_wikdict; "wiktionary" -> R.string.cat_wiktionary; else -> R.string.cat_freedict }) +
            "\n\n${mb(item.size)} · ${item.license}\n${item.url}")
        .setPositiveButton(R.string.download) { _, _ -> download(item) }.setNegativeButton(R.string.cancel, null).show()

    private fun download(item: CatalogItem) {
        info.text = getString(R.string.downloading, "${title(item)} ${item.from} → ${item.to}")
        launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url(item.url).build()).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        Importer.importStream(item.url.substringAfterLast('/'), resp.body!!.byteStream(), prefs.dictsDir, cacheDir)
                    }
                }
            }
            r.fold({ toast(getString(R.string.added, it.joinToString { d -> d.name })) }, { toast(getString(R.string.failed, it.message)) })
            show()
        }
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
