package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import android.widget.Toast
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.packs.Component
import io.github.kafeg.inkpeek.packs.Packs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** InkPeek's published pairs, into the app's language first; a tap downloads one (like the other dictionaries' page). */
class PacksActivity : Activity(), CoroutineScope by MainScope() {
    private lateinit var prefs: Prefs
    private var manifest: List<Component> = emptyList()
    private var pairs: List<Pair<String, String>> = emptyList()
    private lateinit var list: ListView
    private lateinit var info: TextView

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Ink.localized(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = Ink.dp(this, 16)
        val col = Ink.vertical(this).apply { setPadding(pad, pad, pad, pad) }
        info = Ink.text(this, getString(R.string.loading_lists), 15f)
        list = ListView(this)
        col.addView(Ink.text(this, getString(R.string.packs_page), 24f, bold = true))
        col.addView(Ink.text(this, getString(R.string.packs_page_d), 14f), Ink.lp().apply { topMargin = Ink.dp(this@PacksActivity, 6) })
        col.addView(info, Ink.lp().apply { topMargin = Ink.dp(this@PacksActivity, 6) })
        col.addView(list, Ink.lp(h = 0, weight = 1f))
        setContentView(col)
        list.setOnItemClickListener { _, _, i, _ -> confirm(pairs[i]) }
        launch {
            val packs = prefs.packs
            val r = withContext(Dispatchers.IO) { runCatching { packs.list() } }
            manifest = r.getOrNull() ?: withContext(Dispatchers.IO) { packs.cachedList() }.orEmpty()
            r.exceptionOrNull()?.let { toast(getString(R.string.failed, it.message)) }
            show()
        }
    }

    override fun onResume() { super.onResume(); PackJob.listener = { if (!isDestroyed) show() } }
    override fun onPause() { PackJob.listener = null; super.onPause() }
    override fun onDestroy() { cancel(); super.onDestroy() }

    private fun name(c: String) = java.util.Locale(c).getDisplayLanguage(resources.configuration.locales[0]).replaceFirstChar { it.uppercase() }
    private fun title(p: Pair<String, String>) = "${name(p.first)} → ${name(p.second)}"
    /** What the pair still needs here: shared models and langid already installed are not downloaded again. */
    private fun missing(p: Pair<String, String>, inst: Map<String, String> = prefs.packs.installed()) = Packs.needed(manifest, listOf(p)).filter { inst[it.id] != it.sha256 }

    private fun show() {
        PackJob.error?.let { toast(getString(R.string.failed, it.message)); PackJob.error = null }
        val installed = prefs.packs.installedPairs().toSet(); val inst = prefs.packs.installed()
        pairs = Packs.pairs(manifest).sortedWith(compareBy({ it.second != prefs.answerCode }, { name(it.first) }, { name(it.second) }))
        info.text = if (PackJob.busy) PackJob.status.ifEmpty { "…" } else getString(R.string.n_of_m, pairs.count { it in installed }, pairs.size)
        list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, pairs.map { p ->
            title(p) + "   " + if (p in installed) "✓ " + getString(R.string.pack_installed) else "%.0f MB".format(missing(p, inst).sumOf { it.size } / 1e6)
        })
    }

    private fun confirm(p: Pair<String, String>) {
        if (p in prefs.packs.installedPairs()) { toast(getString(R.string.pack_installed)); return }
        if (PackJob.busy) { toast(PackJob.status); return }
        val need = missing(p)
        AlertDialog.Builder(this).setTitle(title(p)).setMessage(getString(R.string.pack_confirm, (need.sumOf { it.size } / 1_000_000).coerceAtLeast(1)))
            .setPositiveButton(R.string.download) { _, _ ->
                // The new pair is in use at once.
                prefs.pairs = prefs.pairs?.plus("${p.first}-${p.second}")
                PackJob.run(this, need, emptyList())
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
