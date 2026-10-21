package io.github.kafeg.inkpeek.ui

import android.view.View
import android.widget.LinearLayout
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.ai.Kind
import io.github.kafeg.inkpeek.stardict.Gloss
import io.github.kafeg.inkpeek.stardict.Hit
import io.github.kafeg.inkpeek.stardict.StarDict
import io.github.kafeg.inkpeek.stardict.shortMeaning
import io.github.kafeg.inkpeek.stardict.Head
import io.github.kafeg.inkpeek.stardict.headLine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Dictionary tab: a word → one line per dictionary (the packs first), tap for the article; a phrase → the word-by-word gloss. */
class DictionaryPage(private val card: CardActivity) {
    private var page: LinearLayout? = null
    private var job: Job? = null
    private var glossCache: List<Gloss>? = null
    /** "←" and Back: from an article to the list, from a word to the gloss; null at the top. */
    var back: (() -> Unit)? = null; private set

    fun fill(p: LinearLayout) { page = p; if (card.kind == Kind.WORD) articles(card.query, null) else gloss() }

    /** [word]'s articles in this tab, "←" back to the gloss; for the gloss rows under other tabs too. */
    fun show(word: String) { page = card.page(CardActivity.DICT, fill = false); card.open(CardActivity.DICT); articles(word) { gloss() } }

    private fun up(to: (() -> Unit)?) { back = to?.let { f -> { back = null; card.showBack(); f() } }; card.showBack() }

    private fun articles(word: String, to: (() -> Unit)?) {
        val p = page ?: return
        job?.cancel(); p.removeAllViews(); up(to)
        val wait = Ink.text(card, "…", card.size); p.addView(wait)
        job = card.launch {
            val notReady = ArrayList<StarDict>(); val lib = card.prefs.library()
            val res = withContext(Dispatchers.IO) { runCatching { lib.lookup(word, card.prefs.answerCode, notReady) to lib.all().isEmpty() } }
            p.removeView(wait)
            val (hits, none) = res.getOrElse { card.note(p, card.getString(R.string.dict_error, it.message)); return@launch }
            if (notReady.isNotEmpty()) card.note(p, card.getString(R.string.preparing, notReady.joinToString { it.name }))
            if (hits.isEmpty() && notReady.isEmpty()) card.note(p, card.getString(if (none) R.string.lang_no_packs else R.string.not_found))
            hits.forEach { (dict, list) ->
                val h = list.first()
                // The pack's first line as it is, "das Haus (Hauses, мн. Häuser) — дом, здание" (without the grammar when it
                // is off: "Haus — дом, здание"); another dictionary's first meaning.
                val packLine = Regex("""<p class="head">(.*?)</p>""").find(h.html)?.groupValues?.get(1)
                val meaning = headLine(h.html)?.let { Head(h.word, it).meaning } ?: runCatching { shortMeaning(h.html, h.word) }.getOrDefault("")
                val first = packLine?.takeIf { card.prefs.packGrammar } ?: ("<b>${Ink.escape(h.word)}</b>" + if (meaning.isBlank()) "" else " — " + Ink.escape(meaning))
                val form = if (h.word.equals(word, true)) "" else "${Ink.escape(word)}: "
                p.addView(Ink.text(card, "$form$first<br><small>${Ink.escape(card.source(dict))}</small>", card.size, html = true).apply {
                    val q = Ink.dp(card, 6); setPadding(0, q, 0, q)
                    setOnClickListener { article(dict, list, word) { articles(word, to) } }
                    TextActions.selectable(this) { t -> t.lastIndexOf('\n').takeIf { it > 0 } ?: t.length } // not the source line
                })
            }
        }
    }

    /** A dictionary's full entries for [word]; "←" (or Back) returns to the list. */
    private fun article(dict: StarDict, list: List<Hit>, word: String, to: () -> Unit) {
        val p = page ?: return
        job?.cancel(); p.removeAllViews(); up(to)
        p.addView(Ink.text(card, card.source(dict), card.size * 0.72f, bold = true).also { TextActions.selectable(it) }, Ink.lp().apply { bottomMargin = Ink.dp(card, 8) })
        list.forEach { h -> p.addView(Ink.text(card, (if (h.word.equals(word, true)) "" else "<b>${h.word}</b><br>") + h.html, card.size, html = true)
            .also { TextActions.selectable(it) }) }
        card.scrollTop()
    }

    private fun gloss() {
        val p = page ?: return
        job?.cancel(); p.removeAllViews(); up(null)
        job = rows(p) { w -> articles(w) { gloss() } }
    }

    /** The gloss under another tab's text (offline, or when AI fails), in that tab's [scope]; a tap opens the word here. */
    fun under(p: LinearLayout, scope: CoroutineScope): Job { p.addView(View(card), Ink.lp(h = Ink.dp(card, 12))); return rows(p, scope) { show(it) } }

    /** Word by word from the packs, offline: base form and first meanings; [tap] opens a word. */
    private fun rows(p: LinearLayout, scope: CoroutineScope = card, tap: (String) -> Unit): Job {
        val wait = Ink.text(card, "…", card.size); p.addView(wait)
        return scope.launch {
            val g = glossCache ?: withContext(Dispatchers.IO) {
                runCatching { card.prefs.library().gloss(card.query, card.prefs.answerCode) }.getOrDefault(emptyList())
            }.also { glossCache = it }
            p.removeView(wait)
            card.note(p, card.getString(R.string.gloss_title))
            if (g.isEmpty()) card.note(p, card.getString(R.string.not_found))
            g.forEach { x ->
                // The same first line as the header and the Dictionary tab: "der Hund (Hundes, мн. Hunde) — собака";
                // a form names itself first: "schläft: schlafen (schläft, schlief, hat geschlafen) — спать".
                val line = x.head?.let { Ink.escape(if (card.prefs.packGrammar) it.line else "${it.word} — ${it.meaning}") }
                val text = when {
                    line == null -> "<b>${Ink.escape(x.word)}</b>"
                    x.base.equals(x.word, true) -> line
                    else -> "<b>${Ink.escape(x.word)}</b>: $line"
                }
                p.addView(Ink.text(card, text, card.size, html = true).apply {
                    val q = Ink.dp(card, 4); setPadding(0, q, 0, q)
                    setOnClickListener { tap(x.base.ifBlank { x.word }) }
                    TextActions.selectable(this) { it.length }
                })
            }
        }
    }
}
