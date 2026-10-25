package io.github.kafeg.inkpeek.mt

import java.io.File

/** AI translation on the device (llama.cpp, an optional pack): one model held, freed when idle or the app goes away. */
object Llm {
    /** Loaded only where it can run: its kernels use int8 dot product (see Device). */
    private val native by lazy { System.loadLibrary("inkpeek_llm") }
    private external fun translateNative(path: String, prompt: ByteArray, maxTokens: Int, threads: Int): ByteArray
    private external fun releaseNative()
    private val idle = java.util.concurrent.Executors.newSingleThreadScheduledExecutor { r -> Thread(r, "inkpeek-llm-idle").apply { isDaemon = true } }
    private var freeing: java.util.concurrent.ScheduledFuture<*>? = null
    @Volatile private var loaded = false

    /** Blocking. [template] with {from}, {to}, {text}; [names]: language codes → the names the model was taught. */
    fun translate(file: File, template: String, names: Map<String, String>, from: String, to: String, text: String, keep: Boolean): String = synchronized(Engine.lock) { native
        freeing?.cancel(false)
        Engine.release() // Mozilla's or OPUS-MT's models: one big model in memory at a time
        val threads = Runtime.getRuntime().availableProcessors().coerceIn(1, 8) / 2
        // Long text in pieces of whole sentences: the model's context is 2048 tokens.
        val out = chunks(text, from).joinToString(" ") { piece ->
            val prompt = template.replace("{from}", names[from] ?: from).replace("{to}", names[to] ?: to).replace("{text}", piece)
            loaded = true
            String(translateNative(file.path, prompt.toByteArray(Charsets.UTF_8), piece.length * 2 + 32, threads.coerceAtLeast(1)), Charsets.UTF_8).trim()
        }
        if (keep) freeing = idle.schedule({ release() }, 2, java.util.concurrent.TimeUnit.MINUTES) else release()
        out
    }

    /** Blocks while a translation runs: not on the main thread. */
    fun release() = synchronized(Engine.lock) { if (loaded) { native; releaseNative(); loaded = false } }

    private fun chunks(text: String, lang: String): List<String> {
        if (text.length <= 1200) return listOf(text)
        val b = java.text.BreakIterator.getSentenceInstance(java.util.Locale(lang)).apply { setText(text) }
        val out = mutableListOf<String>(); var cur = StringBuilder(); var a = b.first(); var e = b.next()
        while (e != java.text.BreakIterator.DONE) {
            val s = text.substring(a, e)
            if (cur.length + s.length > 1200 && cur.isNotEmpty()) { out += cur.toString().trim(); cur = StringBuilder() }
            cur.append(s); a = e; e = b.next()
        }
        if (cur.isNotBlank()) out += cur.toString().trim()
        return out
    }
}
