package io.github.kafeg.inkpeek.ui

import android.widget.LinearLayout
import android.widget.TextView
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.ai.Ask
import io.github.kafeg.inkpeek.ai.Kind
import io.github.kafeg.inkpeek.ai.Action
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/**
 * The card's one AI tab: Analysis (Grammar, Words) and Context (Explanation, Examples) as its sub-tabs, under the app's
 * own headings — only the modules that are on; with one, no sub-tabs.
 */
class AiPage(private val card: CardActivity) {
    /** The open sub-tab: "analysis" or "context". */
    var by = ""

    private var job: kotlinx.coroutines.Job? = null

    fun fill(p: LinearLayout) {
        job?.cancel(); p.removeAllViews(); card.bar(CardActivity.AI, null)
        val subs = card.aiModules; if (subs.isEmpty()) return
        job = card.launch {
            val blocked = withContext(Dispatchers.IO) { subs.associateWith { card.answers.block(ask(it)) } }
            // A module that can answer now first (offline: the one with a saved answer).
            if (by !in subs || blocked[by] != null) by = subs.firstOrNull { blocked[it] == null } ?: subs.first()
            if (subs.size > 1) {
                val seg = Ink.subTabs(card, subs.map { it to Ink.label(card, Action(it)) }, by, card.size * 0.75f) { k ->
                    blocked[k]?.let { card.toast(card.getString(it)) } ?: run { by = k; fill(p) }
                }
                subs.filter { blocked[it] != null }.forEach { seg.findViewWithTag<android.view.View>(it)?.alpha = Ink.OFF }
                card.bar(CardActivity.AI, seg)
            }
            val out = Ink.text(card, "", card.size).also { TextActions.selectable(it) }; p.addView(out)
            blocked[by]?.let { out.text = card.getString(it); card.forget(CardActivity.AI); return@launch }
            val tab = by
            await(out, ask(tab)).fold({ card.markdown(out, render(tab, it)) }, { e ->
                // The error; the tab asks again next time.
                out.text = aiError(card, card.prefs, card.online, e)
                card.forget(CardActivity.AI)
            })
        }
    }

    fun ask(tab: String) = if (tab == "context") Ask.CONTEXT else Ask.ANALYSIS

    /** The answer, with "Thinking…" in [out] while it is asked; the other AI requests go out with it. */
    suspend fun CoroutineScope.await(out: TextView, ask: Ask): Result<JSONObject> {
        val d = card.answers.all(card, ask, card.shownAsks)
        val dots = if (d.isCompleted) null else launch { var n = 0; while (true) { out.text = card.getString(R.string.thinking) + ".".repeat(n % 3 + 1); n++; delay(700) } }
        return d.await().also { dots?.cancel() }
    }

    private fun JSONObject.str(k: String) = optString(k).trim()

    /** Markdown of a tab from its answer. */
    fun render(tab: String, j: JSONObject): String {
        val kind = card.kind; val s = { id: Int -> card.getString(id) }
        fun list(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).joinToString("\n") { "- " + a.optString(it).trim().removePrefix("- ") } }.orEmpty()
        fun pairs(k: String) = j.optJSONArray(k)?.let { a -> (0 until a.length()).mapNotNull { a.optJSONObject(it) }
            .joinToString("\n") { e -> "- " + e.str("text") + "\n*" + e.str("translation") + "*" } }.orEmpty()
        return when (tab) {
            "analysis" -> sections(s(R.string.sec_grammar) to j.str("grammar"),
                s(R.string.sec_words) to list("words"), s(R.string.sec_rel) to list("related"), s(R.string.sec_syn) to list("synonyms"))
            "context" -> sections(s(R.string.sec_explanation) to j.str("explanation"),
                s(R.string.sec_examples) to pairs("examples"), s(R.string.sec_expr) to pairs("idioms"), s(R.string.sec_ety) to j.str("origin"))
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
