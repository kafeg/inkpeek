package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.ScrollView
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.mt.Device
import io.github.kafeg.inkpeek.mt.Engine
import io.github.kafeg.inkpeek.packs.Component
import io.github.kafeg.inkpeek.packs.Packs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The offline translation method for all pairs (Languages → InkPeek): Mozilla through English, OPUS-MT direct, an AI model.
 * The app downloads what the installed pairs need; a method this device cannot run is dimmed with the reason. Downloaded
 * models stay when the method changes (switching back costs nothing); the list under the methods removes one.
 */
class TranslationActivity : Activity(), CoroutineScope by MainScope() {
    private lateinit var prefs: Prefs
    private lateinit var device: Device
    private lateinit var list: LinearLayout
    private var manifest: List<Component> = emptyList()
    private val dlg = Rows.JobDialog(this)

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Ink.localized(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        Ink.theme(this)
        super.onCreate(savedInstanceState)
        prefs = Prefs(this); device = Device.of(this)
        val pad = Ink.dp(this, 16)
        val col = Ink.vertical(this).apply { setPadding(pad, pad, pad, pad) }
        col.addView(Rows.header(this, getString(R.string.mt_t)))
        col.addView(Rows.note(this, getString(R.string.mt_this, String.format(resources.configuration.locales[0], "%.1f", device.totalMb / 1024f),
            getString(if ("asimddp" in device.features) R.string.mt_yes else R.string.mt_no))))
        list = Ink.vertical(this); col.addView(list)
        setContentView(ScrollView(this).apply { addView(col) })
        launch {
            val packs = prefs.packs
            manifest = withContext(Dispatchers.IO) { runCatching { packs.list() }.getOrNull() ?: packs.cachedList().orEmpty() }
            show()
        }
    }

    override fun onResume() {
        super.onResume()
        Downloads.listener = { if (!isDestroyed) { dlg.update(); if (!Downloads.busy) { show(); measure() } } }
        if (Downloads.busy) dlg.show() else dlg.update()
    }
    override fun onPause() { Downloads.listener = null; super.onPause() }
    override fun onDestroy() { dlg.dismiss(); cancel(); super.onDestroy() }

    private fun kind(method: String) = when (method) { "direct" -> "opus"; "llm" -> "llm"; else -> null }

    /** The models [method] needs for the installed pairs (none for Mozilla: the pairs' packs have them). */
    private fun models(method: String): List<Component> = kind(method)?.let { k ->
        Packs.needed(manifest, prefs.packs.installedPairs(), method).filter { it.kind == k } } ?: emptyList()

    /** Why this device cannot run [method], or null. */
    private fun cannot(method: String): String? {
        val k = kind(method) ?: return null
        val c = manifest.firstOrNull { it.kind == k } ?: return getString(R.string.mt_no_models)
        return when (val why = device.cannot(c)) {
            is Device.Cannot.Memory -> getString(R.string.mt_cannot_mem, String.format(resources.configuration.locales[0], "%.0f", why.needMb / 1000f + 0.5f), String.format(resources.configuration.locales[0], "%.1f", why.haveMb / 1024f))
            is Device.Cannot.Cpu -> getString(R.string.mt_cannot_cpu)
            null -> null
        }
    }

    private fun show() {
        list.removeAllViews()
        val installed = prefs.packs.installed(); val pairs = prefs.packs.installedPairs()
        listOf(Triple("off", R.string.mt_off_t, R.string.mt_off_d), Triple("fast", R.string.mt_fast_t, R.string.mt_fast_d), Triple("direct", R.string.mt_direct_t, R.string.mt_direct_d),
            Triple("llm", R.string.mt_llm_t, R.string.mt_llm_d)).forEach { (m, title, about) ->
            val why = cannot(m)
            val lines = mutableListOf(getString(about))
            if (why != null) lines += why
            else if (kind(m) != null) {
                val need = models(m)
                if (need.isNotEmpty()) lines += getString(R.string.mt_need, need.size, (need.sumOf { it.size } / 1_000_000).toInt()) +
                    if (need.all { installed[it.id] == it.sha256 }) " · " + getString(R.string.mt_have) else ""
                val bare = pairs.filter { (x, y) -> manifest.none { it.kind == kind(m) && "$x-$y" in it.covers } }
                if (bare.isNotEmpty()) lines += getString(R.string.mt_none_for, bare.joinToString { (x, y) -> "$x→$y" })
            }
            prefs.mtSpeed(m).takeIf { it > 0 }?.let { lines += getString(R.string.mt_speed, String.format(resources.configuration.locales[0], "%.1f", it / 1000f)) }
            val on = prefs.mtMethod == m
            val box = Ink.vertical(this).apply {
                background = Ink.border(this@TranslationActivity, if (on) 6 else 2, 8); val q = Ink.dp(this@TranslationActivity, 12); setPadding(q, q, q, q)
                addView(Ink.text(this@TranslationActivity, (if (on) "✓ " else "") + getString(title), 17f, bold = true))
                addView(Ink.text(this@TranslationActivity, lines.joinToString("\n"), 14f), Ink.lp().apply { topMargin = Ink.dp(this@TranslationActivity, 4) })
                if (why != null) alpha = Ink.OFF
                setOnClickListener { if (why != null) Ink.toast(this@TranslationActivity, why) else choose(m, getString(title)) }
            }
            list.addView(box, Ink.lp().apply { topMargin = Ink.dp(this@TranslationActivity, 12) })
        }
        // The models on this device, of every method: a ✕ frees the space (the method falls back to Mozilla for its pairs).
        val have = manifest.filter { (it.kind == "opus" || it.kind == "llm") && it.id in installed }
        if (have.isNotEmpty()) list.addView(Rows.heading(this, getString(R.string.mt_downloaded)))
        have.forEach { c ->
            list.addView(Rows.check(this, title(c), getString(R.string.pack_size, (c.size / 1_000_000).toInt()), true, remove = {
                Rows.confirm(this, title(c) + "\n\n" + getString(R.string.mt_remove_q, (c.size / 1_000_000).toInt())) {
                    if (Downloads.busy) dlg.show() else { Downloads.packs(this, emptyList(), listOf(c), getString(R.string.mt_removed)); dlg.show() } }
            }) {}.apply { getChildAt(0).visibility = android.view.View.INVISIBLE })
        }
    }

    /** "OPUS-MT · DE EN ES FR PT → PL RU", "Hy-MT2 1.8B": a model by what it translates. */
    private fun title(c: Component): String = if (c.kind == "llm") c.name.ifBlank { c.id } else {
        fun side(i: Int) = c.covers.map { it.split('-')[i].uppercase() }.distinct().sorted().joinToString(" ")
        "OPUS-MT · ${side(0)} → ${side(1)}"
    }

    /** [method] for all pairs: its missing models downloaded (one dialog); the other methods' models stay. */
    private fun choose(method: String, title: String) {
        if (method == prefs.mtMethod) return
        if (Downloads.busy) { dlg.show(); return }
        val installed = prefs.packs.installed()
        val get = models(method).filter { installed[it.id] != it.sha256 }
        if (get.isEmpty()) { prefs.mtMethod = method; show(); measure(); return }
        InkDialog(this).title(title).message(getString(R.string.mt_get, (get.sumOf { c -> c.size } / 1_000_000).toInt())).button(R.string.cancel).button(R.string.download) {
            prefs.mtMethod = method; prefs.setMtSpeed(method, 0)
            Downloads.packs(this, get, emptyList(), getString(R.string.mt_done, title)); dlg.show()
        }.show()
    }

    /** One phrase translated twice (the second without the load): "≈ N s per phrase" under the method, for this device. */
    private fun measure() {
        val m = prefs.mtMethod
        if (kind(m) == null || prefs.mtSpeed(m) > 0) return
        val installed = prefs.packs.installed()
        val c = models(m).firstOrNull { installed[it.id] == it.sha256 } ?: return
        val (x, y) = prefs.packs.installedPairs().firstOrNull { (x, y) -> "$x-$y" in c.covers } ?: return
        val phrase = SAMPLE[x] ?: return
        launch {
            val ms = withContext(Dispatchers.IO) { runCatching {
                val once = { if (c.kind == "opus") Engine.translateDirect(prefs.packs.opusDir(c.id), x, c.tags[y], phrase, keep = true)
                    else io.github.kafeg.inkpeek.mt.Llm.translate(prefs.packs.llmFile(c.id), c.template, c.names, x, y, phrase, keep = true) }
                once()
                val t0 = System.nanoTime(); once(); (System.nanoTime() - t0) / 1_000_000
            }.getOrDefault(0L) }
            if (ms > 0) { prefs.setMtSpeed(m, ms.toInt()); show() }
        }
    }

    companion object {
        /** A phrase of an ordinary length per book language, for the speed test. */
        private val SAMPLE = mapOf("de" to "Er nimmt ihre rechte Hand und legt sie vorsichtig auf den Schläger.",
            "en" to "He takes her right hand and carefully puts it on the racket.", "ru" to "Он берёт её правую руку и осторожно кладёт на ракетку.",
            "fr" to "Il prend sa main droite et la pose doucement sur la raquette.", "es" to "Toma su mano derecha y la pone con cuidado sobre la raqueta.",
            "it" to "Prende la sua mano destra e la appoggia con cura sulla racchetta.", "pt" to "Ele pega a mão direita dela e a coloca com cuidado na raquete.",
            "pl" to "Bierze jej prawą rękę i ostrożnie kładzie ją na rakiecie.", "uk" to "Він бере її праву руку і обережно кладе на ракетку.")
    }
}
