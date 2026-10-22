package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.app.AlertDialog
import android.os.Bundle
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
        Ink.theme(this)
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = Ink.dp(this, 16)
        val col = Ink.vertical(this).apply { setPadding(pad, pad, pad, pad) }
        info = Ink.text(this, getString(R.string.loading_lists), 15f)
        list = ListView(this)
        col.addView(Rows.header(this, getString(R.string.packs_page)))
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

    private val dlg = Rows.JobDialog(this)

    override fun onResume() {
        super.onResume()
        Downloads.listener = { if (!isDestroyed) { dlg.update(); if (!Downloads.busy) show() } }
        if (Downloads.busy) dlg.show() else dlg.update()
    }
    override fun onPause() { Downloads.listener = null; super.onPause() }
    override fun onDestroy() { dlg.dismiss(); cancel(); super.onDestroy() }

    private fun name(c: String) = Ink.lang(this, c)
    private fun title(p: Pair<String, String>) = "${name(p.first)} → ${name(p.second)}"
    /** What the pair still needs here: shared models and langid already installed are not downloaded again. */
    private fun missing(p: Pair<String, String>, inst: Map<String, String> = prefs.packs.installed()) = Packs.needed(manifest, listOf(p)).filter { inst[it.id] != it.sha256 }

    /** Installed pairs with every part here (a download stopped half-way leaves one to finish: it can be tapped). */
    private fun complete(inst: Map<String, String> = prefs.packs.installed()) =
        prefs.packs.installedPairs().filter { p -> Packs.needed(manifest, listOf(p)).all { it.id in inst } }.toSet()

    private fun show() {
        val inst = prefs.packs.installed(); val installed = complete(inst)
        pairs = Packs.pairs(manifest).sortedWith(compareBy({ it.second != prefs.answerCode }, { name(it.first) }, { name(it.second) }))
        info.text = getString(R.string.n_of_m, pairs.count { it in installed }, pairs.size)
        list.adapter = Rows.adapter(this, pairs.map { p ->
            title(p) to if (p in installed) "✓ " + getString(R.string.pack_installed) else getString(R.string.pack_size, (missing(p, inst).sumOf { it.size } / 1_000_000).toInt())
        })
    }

    private fun confirm(p: Pair<String, String>) {
        if (p in complete()) { toast(getString(R.string.pack_installed)); return }
        if (Downloads.busy) { dlg.show(); return }
        val need = missing(p)
        AlertDialog.Builder(this).setTitle(title(p)).setMessage(getString(R.string.pack_confirm, (need.sumOf { it.size } / 1_000_000).coerceAtLeast(1)))
            .setPositiveButton(R.string.download) { _, _ ->
                // The new pair is in use at once.
                prefs.pairs = prefs.pairs?.plus("${p.first}-${p.second}")
                Downloads.packs(this, need, emptyList(), getString(R.string.pack_done, title(p))); dlg.show()
            }.setNegativeButton(R.string.cancel, null).show()
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
