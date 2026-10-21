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
    /** Full articles (Card → Main): to the next (+1) or previous (-1) dictionary; null in the other views. */
    var turn: ((Int) -> Unit)? = null; private set
    /** "←" and Back: from an article to the list, from a word to the gloss; null at the top. */
    var back: (() -> Unit)? = null; private set

    fun fill(p: LinearLayout) { page = p; if (card.kind == Kind.WORD) articles(card.query, null) else gloss() }

    /** [word]'s articles in this tab, "←" back to the gloss; for the gloss rows under other tabs too. */
    fun show(word: String) { page = card.page(CardActivity.DICT, fill = false); card.open(CardActivity.DICT); articles(word) { gloss() } }

    private fun up(to: (() -> Unit)?) { back = to?.let { f -> { back = null; card.showBack(); f() } }; card.showBack() }

    private fun articles(word: String, to: (() -> Unit)?) {
        val p = page ?: return
        job?.cancel(); p.removeAllViews(); up(to); turn = null
        val wait = Ink.text(card, "…", card.size); p.addView(wait)
        job = card.launch {
            val notReady = ArrayList<StarDict>(); val lib = card.prefs.library()
            val res = withContext(Dispatchers.IO) { runCatching { lib.lookup(word, card.prefs.answerCode, notReady) to lib.all().isEmpty() } }
            p.removeView(wait)
            val (hits, none) = res.getOrElse { card.note(p, card.getString(R.string.dict_error, it.message)); return@launch }
            if (notReady.isNotEmpty()) card.note(p, card.getString(R.string.preparing, notReady.joinToString { it.name }))
            if (hits.isEmpty() && notReady.isEmpty()) card.note(p, card.getString(if (none) R.string.lang_no_packs else R.string.not_found))
            if (card.prefs.cardFull && hits.isNotEmpty() && notReady.isEmpty()) { pages(hits, word, 0, to); return@launch }
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

    /** Full articles: dictionary [i] of [hits] on the page, ◀ ▶ (or a swipe) to the others, in the person's order. */
    private fun pages(hits: List<Pair<StarDict, List<Hit>>>, word: String, i: Int, to: (() -> Unit)?) {
        val p = page ?: return
        p.removeAllViews(); up(to)
        turn = { d -> if (i + d in hits.indices) pages(hits, word, i + d, to) }
        val (dict, list) = hits[i]
        val bar = Ink.horizontal(card).apply { gravity = android.view.Gravity.CENTER_VERTICAL; isBaselineAligned = false }
        val side = Ink.dp(card, 40); val many = hits.size > 1
        if (many) bar.addView(Ink.icon(card, R.drawable.ic_prev, card.getString(R.string.prev), 40) { turn?.invoke(-1) }.apply { alpha = if (i > 0) 1f else 0.3f },
            LinearLayout.LayoutParams(side, side))
        bar.addView(Ink.text(card, (if (many) "${i + 1} / ${hits.size} · " else "") + card.source(dict), card.size * 0.72f, bold = true)
            .apply { gravity = android.view.Gravity.CENTER; maxLines = 2 }, Ink.lp(0, weight = 1f).apply { leftMargin = Ink.dp(card, 8); rightMargin = Ink.dp(card, 8) })
        if (many) bar.addView(Ink.icon(card, R.drawable.ic_next, card.getString(R.string.next), 40) { turn?.invoke(1) }.apply { alpha = if (i < hits.size - 1) 1f else 0.3f },
            LinearLayout.LayoutParams(side, side))
        p.addView(bar, Ink.lp().apply { bottomMargin = Ink.dp(card, 8) })
        list.forEach { h -> p.addView(Ink.text(card, (if (h.word.equals(word, true)) "" else "<b>${h.word}</b><br>") + h.html, card.size, html = true)
            .also { TextActions.selectable(it) }) }
        card.scrollTop()
    }

    /** A dictionary's full entries for [word]; "←" (or Back) returns to the list. */
    private fun article(dict: StarDict, list: List<Hit>, word: String, to: () -> Unit) {
        val p = page ?: return
        job?.cancel(); p.removeAllViews(); up(to); turn = null
        p.addView(Ink.text(card, card.source(dict), card.size * 0.72f, bold = true).also { TextActions.selectable(it) }, Ink.lp().apply { bottomMargin = Ink.dp(card, 8) })
        list.forEach { h -> p.addView(Ink.text(card, (if (h.word.equals(word, true)) "" else "<b>${h.word}</b><br>") + h.html, card.size, html = true)
            .also { TextActions.selectable(it) }) }
        card.scrollTop()
    }

    private fun gloss() {
        val p = page ?: return
        job?.cancel(); p.removeAllViews(); up(null); turn = null
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
