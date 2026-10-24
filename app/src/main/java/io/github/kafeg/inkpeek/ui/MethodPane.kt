package io.github.kafeg.inkpeek.ui

import android.widget.LinearLayout
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.mt.Device
import io.github.kafeg.inkpeek.mt.Engine
import io.github.kafeg.inkpeek.packs.Component
import io.github.kafeg.inkpeek.packs.Packs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The offline translation method for all pairs (Languages → Translation): Off, Mozilla through English, OPUS-MT direct, an
 * AI model. The app downloads what the installed pairs need; a method this device cannot run is dimmed with the reason.
 * Downloaded models stay when the method changes (switching back costs nothing); the list under the methods removes one.
 */
class MethodPane(private val a: MainActivity, private val list: LinearLayout) {
    private val prefs get() = a.prefs
    private val device by lazy { Device.of(a) }
    /** The packs list (Languages loads it). */
    var manifest: List<Component> = emptyList()

    private fun kind(method: String) = when (method) { "direct" -> "opus"; "llm" -> "llm"; else -> null }

    /** The models [method] needs for the installed pairs (none for Mozilla: the pairs' packs have them). */
    private fun models(method: String): List<Component> = kind(method)?.let { k ->
        Packs.needed(manifest, prefs.packs.installedPairs(), method).filter { it.kind == k } } ?: emptyList()

    /** Why this device cannot run [method], or null. */
    private fun cannot(method: String): String? {
        val k = kind(method) ?: return null
        val c = manifest.firstOrNull { it.kind == k } ?: return a.getString(R.string.mt_no_models)
        return when (val why = device.cannot(c)) {
            is Device.Cannot.Memory -> a.getString(R.string.mt_cannot_mem, String.format(a.resources.configuration.locales[0], "%.0f", why.needMb / 1000f + 0.5f), String.format(a.resources.configuration.locales[0], "%.1f", why.haveMb / 1024f))
            is Device.Cannot.Cpu -> a.getString(R.string.mt_cannot_cpu)
            null -> null
        }
    }

    fun show() {
        list.removeAllViews()
        list.addView(Rows.note(a, a.getString(R.string.mt_this, String.format(a.resources.configuration.locales[0], "%.1f", device.totalMb / 1024f),
            a.getString(if ("asimddp" in device.features) R.string.mt_yes else R.string.mt_no))))
        val installed = prefs.packs.installed(); val pairs = prefs.packs.installedPairs()
        listOf(Triple("off", R.string.mt_off_t, R.string.mt_off_d), Triple("fast", R.string.mt_fast_t, R.string.mt_fast_d), Triple("direct", R.string.mt_direct_t, R.string.mt_direct_d),
            Triple("llm", R.string.mt_llm_t, R.string.mt_llm_d)).forEach { (m, title, about) ->
            val why = cannot(m)
            val lines = mutableListOf(a.getString(about))
            if (why != null) lines += why
            else if (kind(m) != null) {
                val need = models(m)
                if (need.isNotEmpty()) lines += a.getString(R.string.mt_need, need.size, (need.sumOf { it.size } / 1_000_000).toInt()) +
                    if (need.all { installed[it.id] == it.sha256 }) " · " + a.getString(R.string.mt_have) else ""
                val bare = pairs.filter { (x, y) -> manifest.none { it.kind == kind(m) && "$x-$y" in it.covers } }
                if (bare.isNotEmpty()) lines += a.getString(R.string.mt_none_for, bare.joinToString { (x, y) -> "$x→$y" })
            }
            prefs.mtSpeed(m).takeIf { it > 0 }?.let { lines += a.getString(R.string.mt_speed, String.format(a.resources.configuration.locales[0], "%.1f", it / 1000f)) }
            val on = prefs.mtMethod == m
            val box = Ink.vertical(a).apply {
                background = Ink.border(a, if (on) 6 else 2, 8); val q = Ink.dp(a, 12); setPadding(q, q, q, q)
                addView(Ink.text(a, (if (on) "✓ " else "") + a.getString(title), 17f, bold = true))
                addView(Ink.text(a, lines.joinToString("\n"), 14f), Ink.lp().apply { topMargin = Ink.dp(a, 4) })
                if (why != null) alpha = Ink.OFF
                setOnClickListener { if (why != null) Ink.toast(a, why) else choose(m, a.getString(title)) }
            }
            list.addView(box, Ink.lp().apply { topMargin = Ink.dp(a, 12) })
        }
        // The models on this device, of every method: a ✕ frees the space (the method falls back to Mozilla for its pairs).
        val have = manifest.filter { (it.kind == "opus" || it.kind == "llm") && it.id in installed }
        if (have.isNotEmpty()) list.addView(Rows.heading(a, a.getString(R.string.mt_downloaded)))
        have.forEach { c ->
            list.addView(Rows.check(a, title(c), a.getString(R.string.pack_size, (c.size / 1_000_000).toInt()), true, remove = {
                Rows.confirm(a, title(c) + "\n\n" + a.getString(R.string.mt_remove_q, (c.size / 1_000_000).toInt())) {
                    if (Downloads.busy) a.showDownload() else { Downloads.packs(a, emptyList(), listOf(c), a.getString(R.string.mt_removed)); a.showDownload() } }
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
        if (Downloads.busy) { a.showDownload(); return }
        val installed = prefs.packs.installed()
        val get = models(method).filter { installed[it.id] != it.sha256 }
        if (get.isEmpty()) { prefs.mtMethod = method; show(); measure(); return }
        InkDialog(a).title(title).message(a.getString(R.string.mt_get, (get.sumOf { c -> c.size } / 1_000_000).toInt())).button(R.string.cancel).button(R.string.download) {
            prefs.mtMethod = method; prefs.setMtSpeed(method, 0)
            Downloads.packs(a, get, emptyList(), a.getString(R.string.mt_done, title)); a.showDownload()
        }.show()
    }

    /** One phrase translated twice (the second without the load): "≈ N s per phrase" under the method, for this device. */
    fun measure() {
        val m = prefs.mtMethod
        if (kind(m) == null || prefs.mtSpeed(m) > 0) return
        val installed = prefs.packs.installed()
        val c = models(m).firstOrNull { installed[it.id] == it.sha256 } ?: return
        val (x, y) = prefs.packs.installedPairs().firstOrNull { (x, y) -> "$x-$y" in c.covers } ?: return
        val phrase = SAMPLE[x] ?: return
        a.launch {
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
