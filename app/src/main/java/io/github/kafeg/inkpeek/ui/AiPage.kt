package io.github.kafeg.inkpeek.ui

import android.widget.LinearLayout
import android.widget.TextView
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.ai.Answers
import io.github.kafeg.inkpeek.ai.Ask
import io.github.kafeg.inkpeek.ai.Kind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** The AI tabs, Analysis and Context: both read the detailed answer, each under the app's own headings. */
class AiPage(private val card: CardActivity) {
    fun fill(p: LinearLayout, tab: String) {
        val out = Ink.text(card, "", card.size); p.addView(out)
        card.launch {
            val r = await(out, Ask.DETAIL)
            r.fold({ card.markdown(out, render(tab, it)) }, { e ->
                // The error, and for a phrase or passage the offline gloss under it; the tab asks again next time.
                out.text = if (e is Answers.Offline) card.getString(R.string.err_offline) else aiError(card, card.prefs, card.online, e)
                card.forget(tab)
                if (card.kind != Kind.WORD) card.dictionary.under(p, this)
            })
        }
    }

    /** The answer, with "Thinking…" in [out] while it is asked. */
    suspend fun CoroutineScope.await(out: TextView, ask: Ask): Result<JSONObject> {
        val d = card.answers.get(card, ask)
        val dots = if (d.isCompleted) null else launch { var n = 0; while (true) { out.text = card.getString(R.string.thinking) + ".".repeat(n % 3 + 1); n++; delay(700) } }
        return d.await().also { dots?.cancel() }
    }

    private fun JSONObject.str(k: String) = optString(k).trim()

    /** Markdown of a tab from its answer. */
    fun render(tab: String, j: JSONObject): String {
        val kind = card.kind; val s = { id: Int -> card.getString(id) }
        return when (tab) {
            "analysis" -> sections(
                s(when (kind) { Kind.WORD -> R.string.sec_usage_word; Kind.PHRASE -> R.string.sec_usage_phrase; else -> R.string.sec_usage_passage }) to j.str("usage"),
                s(if (kind == Kind.WORD) R.string.sec_related else R.string.sec_words) to
                    j.optJSONArray("words")?.let { a -> (0 until a.length()).joinToString("\n") { "- " + a.optString(it).trim().removePrefix("- ") } }.orEmpty())
            "context" -> sections(
                s(R.string.sec_examples) to j.optJSONArray("examples")?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                    .joinToString("\n") { e -> "- " + e.str("text") + "\n*" + e.str("translation") + "*" } }.orEmpty(),
                s(if (kind == Kind.PASSAGE) R.string.sec_meaning else R.string.sec_notes) to j.str("notes"))
            else -> {
                // Translate by AI: its view of the word ("das Haus · die Häuser", the verb's forms, irregular), the translation.
                val facts = if (kind != Kind.WORD) "" else j.str("forms").ifBlank {
                    listOf(j.str("article"), j.str("base")).filter { it.isNotBlank() }.joinToString(" ") + j.str("plural").let { if (it.isBlank()) "" else " · $it" }
                }.let { f -> listOf(f, j.str("pos"), j.str("form_of"), if (j.optBoolean("irregular")) s(R.string.irregular) else "")
                    .filter { it.isNotBlank() }.joinToString(" · ") }
                val m = j.optJSONArray("meanings")?.let { a -> (0 until a.length()).map { "- " + a.getString(it) } }.orEmpty()
                (listOf(if (facts.isBlank()) "" else "*$facts*", j.str("translation")) + m).filter { it.isNotBlank() }.joinToString("\n")
            }
        }
    }

    private fun sections(vararg s: Pair<String, String>) = s.filter { it.second.isNotBlank() }.joinToString("\n\n") { (h, t) -> "**$h**\n$t" }
}
