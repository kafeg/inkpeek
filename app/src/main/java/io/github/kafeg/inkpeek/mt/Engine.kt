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

    /** The pair whose models are held in memory: only one at a time, models of other pairs would add up (killed by Android). */
    private var held: Pair<String, String>? = null

    /** Blocking. [keep] holds the pair's models in memory for the next call (≈ 280–360 MB each); else frees them after use. */
    @Synchronized fun translate(root: File, from: String, to: String, text: String, keep: Boolean): String = native.let {
        if (held != null && held != from to to) { releaseNative(); held = null }
        if (keep) held = from to to
        freeing?.cancel(false)
        steps(from, to).fold(text) { t, (a, b) -> String(translateNative(config(Models.dir(root, a, b)), t.toByteArray(Charsets.UTF_8), keep), Charsets.UTF_8) }
            .also { if (keep) freeing = idle.schedule({ release() }, 3, java.util.concurrent.TimeUnit.MINUTES) }
    }

    @Synchronized fun release() { native; releaseNative(); held = null }

    /** Marian options for one model folder (model, lex shortlist, vocab or src / trg vocab). */
    fun config(d: File): String {
        val fs = d.listFiles().orEmpty()
        fun f(p: (String) -> Boolean) = fs.firstOrNull { p(it.name) }?.path ?: error("Missing model file in ${d.path}")
        val model = f { it.startsWith("model.") }; val lex = f { it.startsWith("lex.") }
        val src = fs.firstOrNull { it.name.startsWith("srcvocab.") }?.path ?: f { it.startsWith("vocab.") }
        val trg = fs.firstOrNull { it.name.startsWith("trgvocab.") }?.path ?: src
        return """
            models: [$model]
            vocabs: [$src, $trg]
            shortlist: [$lex, false]
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
