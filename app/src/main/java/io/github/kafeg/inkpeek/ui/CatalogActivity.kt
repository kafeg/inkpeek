package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.ArrayAdapter
import android.widget.EditText
import android.widget.ListView
import android.widget.Toast
import io.github.kafeg.inkpeek.Prefs
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

/** Public dictionaries (Wiktionary, FreeDict): filter by language pair, tap to download and import. */
class CatalogActivity : Activity(), CoroutineScope by MainScope() {
    private val http = OkHttpClient.Builder().readTimeout(60, TimeUnit.SECONDS).build()
    private var items: List<CatalogItem> = emptyList()
    private lateinit var list: ListView
    private lateinit var info: android.widget.TextView
    private lateinit var filter: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pad = Ink.dp(this, 16)
        val col = Ink.vertical(this).apply { setPadding(pad, pad, pad, pad) }
        info = Ink.text(this, "Loading lists…", 16f)
        filter = EditText(this).apply { hint = "Filter: de-ru, en, ru…"; textSize = 18f; setSingleLine() }
        list = ListView(this)
        col.addView(Ink.text(this, "Get dictionaries", 24f, bold = true))
        col.addView(filter); col.addView(info); col.addView(list, Ink.lp(h = 0, weight = 1f))
        setContentView(col)
        filter.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = show()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        list.setOnItemClickListener { _, _, i, _ -> confirm(shown[i]) }
        launch {
            val loaded = withContext(Dispatchers.IO) {
                listOf(Catalog.WIKTIONARY_URL to Catalog::wiktionary, Catalog.FREEDICT_URL to Catalog::freedict)
                    .flatMap { (url, parse) -> runCatching { parse(get(url)) }.getOrDefault(emptyList()) }
            }
            items = loaded.sortedWith(compareBy({ it.from }, { it.to }, { it.source }))
            show()
        }
    }

    override fun onDestroy() { cancel(); super.onDestroy() }

    private var shown: List<CatalogItem> = emptyList()
    private fun show() {
        val f = filter.text.toString().trim().lowercase()
        shown = items.filter { f.isEmpty() || "${it.from}-${it.to} ${it.title}".lowercase().contains(f) }
        info.text = "${shown.size} of ${items.size}"
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1,
            shown.map { "${it.from} → ${it.to}   ${it.source}   ${mb(it.size)}" })
    }

    private fun mb(b: Long) = if (b <= 0) "" else "%.1f MB".format(b / 1e6)

    private fun get(url: String): String = http.newCall(Request.Builder().url(url).build()).execute().use {
        if (!it.isSuccessful) error("HTTP ${it.code}"); it.body!!.string()
    }

    private fun confirm(item: CatalogItem) = AlertDialog.Builder(this).setTitle(item.title)
        .setMessage("${mb(item.size)} · ${item.license}\n${item.url}")
        .setPositiveButton("Download") { _, _ -> download(item) }.setNegativeButton("Cancel", null).show()

    private fun download(item: CatalogItem) {
        info.text = "Downloading ${item.title}…"
        launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    http.newCall(Request.Builder().url(item.url).build()).execute().use { resp ->
                        if (!resp.isSuccessful) error("HTTP ${resp.code}")
                        Importer.importStream(item.url.substringAfterLast('/'), resp.body!!.byteStream(), Prefs(this@CatalogActivity).dictsDir, cacheDir)
                    }
                }
            }
            r.fold({ Toast.makeText(this@CatalogActivity, "Added: ${it.joinToString { d -> d.name }}", Toast.LENGTH_LONG).show() },
                { Toast.makeText(this@CatalogActivity, "Failed: ${it.message}", Toast.LENGTH_LONG).show() })
            show()
        }
    }
}
