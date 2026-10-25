package io.github.kafeg.inkpeek.mt

import java.io.File

/** Offline translation with Bergamot (native, arm64). Through English when neither side is English. */
object Engine {
    /** The native library loads on the first translation, off the main thread. */
    private val native by lazy { System.loadLibrary("inkpeek_mt") }

    private external fun translateNative(cfg: String, text: ByteArray, keep: Boolean): ByteArray
    /** Held models (300–560 MB) are freed after a few idle minutes: the process lives on next to the reader. */
    private val idle = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "inkpeek-mt-idle").apply { isDaemon = true } }
    private var freeing: java.util.concurrent.ScheduledFuture<*>? = null
    private external fun releaseNative()

    /** Model directories a [from]→[to] translation needs, in order. */
    fun steps(from: String, to: String): List<Pair<String, String>> = when {
        from == to -> emptyList()
        from == "en" || to == "en" -> listOf(from to to)
        else -> listOf(from to "en", "en" to to)
    }

    fun ready(root: File, from: String, to: String) = steps(from, to).all { (a, b) -> Models.installed(root, a, b) }

    /** One lock for this and [Llm]: each frees the other's model first, under two locks that would deadlock. */
    internal val lock = Any()

    /** What is held in memory (a Mozilla pair or an OPUS-MT model): one at a time, others would add up (killed by Android). */
    private var held: String? = null

    private fun hold(key: String, keep: Boolean) {
        Llm.release() // an AI model is not kept next to these
        if (held != null && held != key) { releaseNative(); held = null }
        if (keep) held = key
        freeing?.cancel(false)
    }

    private fun idleLater(keep: Boolean) { if (keep) freeing = idle.schedule({ release() }, 3, java.util.concurrent.TimeUnit.MINUTES) }

    private fun run(dir: File, text: String, keep: Boolean) = String(translateNative(config(dir), text.toByteArray(Charsets.UTF_8), keep), Charsets.UTF_8)

    /**
     * Blocking. [keep] holds the pair's models in memory for the next call (≈ 280–360 MB each); else frees them after use.
     * [english]: the English of the step in between, when there is one.
     */
    fun translate(root: File, from: String, to: String, text: String, keep: Boolean, english: ((String) -> Unit)? = null): String = synchronized(lock) { native
        hold("$from-$to", keep)
        try { steps(from, to).fold(text) { t, (a, b) -> run(Models.dir(root, a, b), t, keep).also { if (b == "en" && to != "en") english?.invoke(it) } } }
        finally { idleLater(keep) }
    }

    /**
     * Blocking, with an OPUS-MT model ([dir]): straight [from] → target. A multi-target model needs its [tag] (">>rus<<")
     * before every sentence, so the text goes sentence by sentence (Android's sentence breaks for [from]).
     */
    fun translateDirect(dir: File, from: String, tag: String?, text: String, keep: Boolean): String = synchronized(lock) { native
        hold(dir.path, keep)
        val parts = if (tag == null) listOf(text) else sentences(text, from)
        try { parts.joinToString(" ") { s -> if (s.isBlank()) s else run(dir, if (tag == null) s else "$tag $s", keep) }.trim() } finally { idleLater(keep) }
    }

    private fun sentences(text: String, lang: String): List<String> {
        val b = java.text.BreakIterator.getSentenceInstance(java.util.Locale(lang)).apply { setText(text) }
        return generateSequence(b.first()) { b.next().takeIf { it != java.text.BreakIterator.DONE } }.zipWithNext { a, c -> text.substring(a, c).trim() }.toList()
    }

    /** Blocks while a translation runs: not on the main thread. */
    fun release() = synchronized(lock) { native; releaseNative(); held = null }

    /** Marian options for one model folder (model, lex shortlist, vocab or src / trg vocab). */
    fun config(d: File): String {
        val fs = d.listFiles().orEmpty()
        fun f(p: (String) -> Boolean) = fs.firstOrNull { p(it.name) }?.path ?: error("Missing model file in ${d.path}")
        val model = f { it.startsWith("model.") }
        // Mozilla's models have a lexical shortlist; OPUS-MT's have none (slower, still fine on a phone).
        val lex = fs.firstOrNull { it.name.startsWith("lex.") }?.path
        val src = fs.firstOrNull { it.name.startsWith("srcvocab.") }?.path ?: f { it.startsWith("vocab.") }
        val trg = fs.firstOrNull { it.name.startsWith("trgvocab.") }?.path ?: src
        return """
            models: [$model]
            vocabs: [$src, $trg]
            ${if (lex != null) "shortlist: [$lex, false]" else ""}
            beam-size: 1
            normalize: 1.0
            word-penalty: 0
            max-length-break: 128
            mini-batch-words: 1024
            max-length-factor: 2.0
            skip-cost: true
            cpu-threads: 1
            quiet: true
            quiet-translation: true
            gemm-precision: ${if (model.contains("alphas")) "int8shiftAlphaAll" else "int8shiftAll"}
            alignment: soft
        """.trimIndent()
    }
}
