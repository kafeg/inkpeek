package io.github.kafeg.inkpeek.mt

import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/** The offline translation of one [text] into the app's language: started once, shared, saved per model version. */
class OfflineTranslation(private val prefs: Prefs, private val store: Store, private val text: String, private val roomy: () -> Boolean,
                         private val device: () -> Device? = { null }) {
    /** [from] the detected language, [other] a language without a pack; [text] null when no translation can be made; [by] the model ("OPUS-MT", "" for Mozilla). */
    class Result(val from: String?, val other: String?, val text: kotlin.Result<String>?, val by: String = "")

    private var job: Deferred<Result>? = null

    fun start(scope: CoroutineScope): Deferred<Result> = job ?: scope.async(Dispatchers.IO) {
        runCatching {
            val root = prefs.modelsDir; val to = prefs.answerCode
            val (from, other) = LangId.source(text, to, prefs.packs, prefs.packDictionaries())
            when {
                from == null -> Result(null, other, null)
                from == to -> Result(from, null, kotlin.Result.success(text))
                else -> direct(from, to) ?: if (!Engine.ready(root, from, to)) Result(from, other, null) else {
                    // Saved per model version: an updated model translates again.
                    val installed = prefs.packs.installed()
                    val ver = Engine.steps(from, to).joinToString { (a, b) -> installed["model-$a-$b"].orEmpty() }.hashCode()
                    Result(from, null, runCatching {
                        store.answer(text, to, "offline-$from", ver) ?: Engine.translate(root, from, to, text, keep = roomy())
                            .also { runCatching { store.putAnswer(text, to, "offline-$from", ver, "bergamot", it) } }
                    })
                }
            }
        }.getOrElse { Result(null, null, kotlin.Result.failure(it)) }
    }.also { job = it }

    /** The chosen method's model for [from] → [to] when it is installed and this device runs it; null: Mozilla. */
    private fun direct(from: String, to: String): Result? {
        val kind = when (prefs.mtMethod) { "direct" -> "opus"; "llm" -> "llm"; else -> return null }
        val packs = prefs.packs; val installed = packs.installed()
        val c = packs.cachedList().orEmpty().firstOrNull { it.kind == kind && "$from-$to" in it.covers && installed[it.id] == it.sha256 } ?: return null
        if (device()?.cannot(c) != null) return null
        val ver = c.sha256.hashCode()
        return runCatching {
            store.answer(text, to, "offline-$from", ver) ?: when (kind) {
                "opus" -> Engine.translateDirect(packs.opusDir(c.id), from, c.tags[to], text, keep = roomy())
                else -> Llm.translate(packs.llmFile(c.id), c.template, c.names, from, to, text, keep = roomy())
            }.also { runCatching { store.putAnswer(text, to, "offline-$from", ver, kind, it) } }
        }.fold({ Result(from, null, kotlin.Result.success(it), if (kind == "opus") "OPUS-MT" else c.name) }, { null }) // a failure: Mozilla
    }

    /** The translation, if it is done. */
    fun done(): String? = job?.takeIf { it.isCompleted }?.getCompleted()?.text?.getOrNull()
}
