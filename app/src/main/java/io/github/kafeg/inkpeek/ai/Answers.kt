package io.github.kafeg.inkpeek.ai

import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.Store
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import org.json.JSONObject

/** The AI answers about one [text]: each request started once and shared by the tabs; a saved answer opens offline. */
class Answers(private val prefs: Prefs, private val store: Store, private val text: String, private val study: () -> String? = { null },
              private val online: () -> Boolean) {
    /** The language you learn for this text, as AI is told it ("German"); blocking. */
    private fun name() = study()?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale.ENGLISH) }

    /** No network and no saved answer. */
    class Offline : Exception()

    private val started = HashMap<Ask, Deferred<Result<JSONObject>>>()

    /** The saved answer, else a request; a failed one is asked again next time. */
    fun get(scope: CoroutineScope, ask: Ask): Deferred<Result<JSONObject>> {
        started[ask]?.let { d -> if (!d.isCompleted || d.getCompleted().isSuccess) return d }
        val pr = prefs.provider; val lang = prefs.answerLang; val model = prefs.model(pr)
        return scope.async(Dispatchers.IO) { runCatching {
            val name = name(); val ver = ask.version(name)
            store.answer(text, lang, ask.section, ver)?.let { return@runCatching JSONObject(it) }
            if (!online()) throw Offline()
            Ai.ask(pr, prefs.effectiveKey(pr), model, ask, text, lang, prefs.url(pr), source(), name)
                .also { runCatching { store.putAnswer(text, lang, ask.section, ver, model, it.toString()) } }
        } }.also { started[ask] = it }
    }

    /** [first], then the others the card shows ([shown]) at once: whichever AI tab opens, the others' answers are on their
     *  way and saved. A hidden tab's request is not sent. */
    fun all(scope: CoroutineScope, first: Ask, shown: Collection<Ask>): Deferred<Result<JSONObject>> =
        get(scope, first).also { if (prefs.aiPrefetch) shown.filter { it != first }.forEach { a -> get(scope, a) } }

    /** The text's language as the offline side finds it (fastText, then the pack that knows more words), in English. */
    private fun source(): String? = runCatching {
        val (book, other) = io.github.kafeg.inkpeek.mt.LangId.source(text, prefs.answerCode, prefs.packs, prefs.packDictionaries())
        (book ?: other)?.let { java.util.Locale(it).getDisplayLanguage(java.util.Locale.ENGLISH) }
    }.getOrNull()

    /** Why an AI tab cannot open now (a string id), or null. Blocking. */
    fun block(ask: Ask): Int? {
        val pr = prefs.provider
        return when {
            !prefs.aiOn -> R.string.ai_off
            pr != Provider.CUSTOM && prefs.effectiveKey(pr).isBlank() -> R.string.err_no_key
            prefs.model(pr).isBlank() -> R.string.err_no_model
            !online() && runCatching { store.answer(text, prefs.answerLang, ask.section, ask.version(name())) }.getOrNull() == null -> R.string.err_offline
            else -> null
        }
    }

    /** The quick answer if it is saved: one line for Notes. */
    fun saved(): String? = store.answer(text, prefs.answerLang, Ask.QUICK.section, Ask.QUICK.version(name()))?.let { Store.brief(it) }
}
