package io.github.kafeg.inkpeek.mt

import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async

/** The offline translation of one [text] into the app's language: started once, shared, saved per model version. */
class OfflineTranslation(private val prefs: Prefs, private val store: Store, private val text: String, private val roomy: () -> Boolean) {
    /** [from] the detected language, [other] a language without a pack; [text] null when no translation can be made. */
    class Result(val from: String?, val other: String?, val text: kotlin.Result<String>?)

    private var job: Deferred<Result>? = null

    fun start(scope: CoroutineScope): Deferred<Result> = job ?: scope.async(Dispatchers.IO) {
        runCatching {
            val root = prefs.modelsDir; val to = prefs.answerCode
            val (from, other) = LangId.source(text, to, prefs.packs, prefs.library())
            when {
                from == null || !Engine.ready(root, from, to) -> Result(from, other, null)
                from == to -> Result(from, null, kotlin.Result.success(text))
                else -> {
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

    /** The translation, if it is done. */
    fun done(): String? = job?.takeIf { it.isCompleted }?.getCompleted()?.text?.getOrNull()
}
