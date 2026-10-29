package io.github.kafeg.inkpeek.ui

import android.view.View
import android.widget.LinearLayout
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.ai.Kind
import io.github.kafeg.inkpeek.stardict.Gloss
import io.github.kafeg.inkpeek.stardict.Hit
import io.github.kafeg.inkpeek.stardict.StarDict
import io.github.kafeg.inkpeek.stardict.answer
import io.github.kafeg.inkpeek.stardict.book
import io.github.kafeg.inkpeek.stardict.shortMeaning
import io.github.kafeg.inkpeek.stardict.Head
import io.github.kafeg.inkpeek.stardict.headLine
import io.github.kafeg.inkpeek.stardict.Separable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Dictionary tab: a word → one line per dictionary (the packs first), tap for the article; a phrase → the word-by-word gloss. */
class DictionaryPage(private val card: CardActivity) {
    companion object {
        /**
         * A dictionary's short line for [word]: the pack's first line as it is, "das Haus (Hauses, мн. Häuser) — дом, здание"
         * (without the grammar when it is off), another dictionary's first meaning; the source under it. Also the font page's sample.
         */
        fun line(prefs: io.github.kafeg.inkpeek.Prefs, dict: StarDict, h: Hit, word: String, source: String): String {
            val packLine = Regex("""<p class="head">(.*?)</p>""").find(h.html)?.groupValues?.get(1)
            val first = packLine?.takeIf { prefs.packGrammar } ?: ("<b>${Ink.escape(h.word)}</b>" + meaning(h).let { if (it.isBlank()) "" else " — " + Ink.escape(it) })
            val form = if (h.word.equals(word, true)) "" else "${Ink.escape(word)}: "
            return "$form$first<br><small>${Ink.escape(source)}</small>"
        }

        /** The first meaning of an entry: "дом, здание". */
        fun meaning(h: Hit): String = headLine(h.html)?.let { Head(h.word, it).meaning } ?: runCatching { shortMeaning(h.html, h.word) }.getOrDefault("")

        /** Where a line comes from: "InkPeek · Немецкий → Русский", or another dictionary's name. */
        fun source(ctx: android.content.Context, lib: io.github.kafeg.inkpeek.stardict.Library, d: StarDict): String =
            if (lib.isPack(d)) "InkPeek · " + listOfNotNull(d.book, d.answer).joinToString(" → ") { Ink.lang(ctx, it) }.ifBlank { d.name } else d.name
    }

    private var page: LinearLayout? = null
    private var job: Job? = null
    /** Word by word and the verbs written apart, worked out once per card (from its start for a phrase). */
    private var glossJob: Deferred<Pair<List<Gloss>, List<Pair<Separable.Find, Head?>>>>? = null

    /**
     * Starts word by word in the background: ready when the Dictionary tab or the gloss under an answer opens. Worked out
     * while a dictionary is still being indexed, it is not kept: the next opening asks again.
     */
    fun warm() = glossJob ?: card.async(Dispatchers.IO) {
        val lib = card.prefs.library()
        val ready = lib.all().all { it.ready }
        (runCatching { lib.gloss(card.query, card.prefs.answerCode) }.getOrDefault(emptyList()) to
            runCatching { lib.separable(card.query, card.prefs.answerCode) }.getOrDefault(emptyList()))
            .also { if (!ready) withContext(Dispatchers.Main) { glossJob = null } }
    }.also { glossJob = it }
    /** Full articles (Card → Opening): to the next (+1) or previous (-1) dictionary; null in the other views. */
    var turn: ((Int) -> Unit)? = null; private set
    /** "←" and Back: from an article to the list, from a word to the gloss; null at the top. */
    var back: (() -> Unit)? = null; private set
    /** What the tab shows: "" the list, "a:<dictionary id>" its article, "p:<n>" the n-th of the full articles. */
    var place = ""; private set
    /** A place to open once the lookup is done (coming back to this word in the card). */
    var restore: String? = null

    fun fill(p: LinearLayout) { page = p; if (card.kind == Kind.WORD) articles(card.query, null) else gloss() }


    private fun up(to: (() -> Unit)?) { back = to?.let { f -> { back = null; card.showBack(); f() } }; card.showBack() }

    private fun articles(word: String, to: (() -> Unit)?) {
        val p = page ?: return
        job?.cancel(); p.removeAllViews(); up(to); turn = null; card.scrollTop()
        val wait = Ink.text(card, "…", card.size); p.addView(wait)
        job = card.launch {
            val notReady = ArrayList<StarDict>(); val lib = card.prefs.library()
            // The card's own text in the answer language with a language you learn: its packs.
            val study = if (to == null && word == card.query) withContext(Dispatchers.IO) { card.study } else null
            val res = withContext(Dispatchers.IO) { runCatching { (if (study == null) lib.lookup(word, card.prefs.answerCode, notReady)
                else lib.lookup(word, study, notReady, studyDicts(lib, study))) to lib.all().isEmpty() } }
            p.removeView(wait)
            val (hits, none) = res.getOrElse { card.note(p, card.getString(R.string.dict_error, it.message)); return@launch }
            if (notReady.isNotEmpty()) card.note(p, card.getString(R.string.preparing, notReady.joinToString { it.name }))
            if (hits.isEmpty() && notReady.isEmpty()) card.note(p, card.getString(if (none) R.string.lang_no_packs else R.string.not_found))
            val r = restore; restore = null
            // A section's list (r:<dictionary>:<section>:<page>:<word>): opened over the article it came from.
            r?.takeIf { it.startsWith("r:") }?.split(':', limit = 5)?.takeIf { it.size == 5 }?.let { (_, id, k, n, w) ->
                hits.firstOrNull { it.first.id == id }?.let { (dict, _) -> section(dict, w, k, n.toIntOrNull() ?: 0); return@launch } }
            r?.removePrefix("p:")?.toIntOrNull()?.takeIf { r.startsWith("p:") && it in hits.indices }?.let { pages(hits, word, it, to); card.scrollBack(); return@launch }
            r?.takeIf { it.startsWith("a:") }?.let { id -> hits.firstOrNull { it.first.id == id.removePrefix("a:") } }
                ?.let { (dict, list) -> article(dict, list, word) { articles(word, to) }; card.scrollBack(); return@launch }
            if (study != null) { hits.firstOrNull()?.let { studyBlock(p, word, study, it, to) }; return@launch }
            if (card.prefs.cardFull && hits.isNotEmpty() && notReady.isEmpty()) { pages(hits, word, 0, to); return@launch }
            place = ""
            hits.forEachIndexed { i, (dict, list) ->
                if (i > 0) p.addView(Ink.divider(card))
                p.addView(Ink.opener(card, Ink.text(card, line(card.prefs, dict, list.first(), word, card.source(dict)), card.size, html = true).apply {
                    val q = Ink.dp(card, 6); setPadding(0, q, 0, q); TextActions.selectable(this)
                }, { article(dict, list, word) { articles(word, to) } }))
            }
            card.scrollBack()
        }
    }

    /** The packs answer → [lang] (the language you learn) that the card uses. */
    private fun studyDicts(lib: io.github.kafeg.inkpeek.stardict.Library, lang: String) =
        lib.all().filter { lib.isPack(it) && it.book == card.prefs.answerCode && it.answer == lang }

    /**
     * The language you learn: the line of the pack answer → it (tap: the article, ← back), then the translated words with
     * their own header and frequency from the pack of their language (tap: their own card).
     */
    private fun studyBlock(p: LinearLayout, word: String, lang: String, hit: Pair<StarDict, List<Hit>>, to: (() -> Unit)?) {
        place = ""
        val (dict, list) = hit
        p.addView(Ink.opener(card, Ink.text(card, line(card.prefs, dict, list.first(), word, card.source(dict)), card.size, html = true).apply {
            val q = Ink.dp(card, 6); setPadding(0, q, 0, q); TextActions.selectable(this)
        }, { article(dict, list, word) { articles(word, to) } }))
        p.addView(Ink.divider(card))
        job = card.launch {
            val lib = card.prefs.library(); val ans = card.prefs.answerCode
            val words = withContext(Dispatchers.IO) { io.github.kafeg.inkpeek.Study.words(meaning(list.first())).map { w ->
                // Its own grammar and frequency from the pack of its language (de → ru); none: the word alone.
                val h = runCatching { lib.heads(w, ans, lib.all().filter { lib.isPack(it) && it.book == lang }).firstOrNull() }.getOrNull()
                Triple(w, h, h?.let { runCatching { lib.rank(it.word, lang) }.getOrDefault(0) } ?: 0)
            } }
            words.forEach { (w, h, rank) ->
                val head = h?.let { Ink.escape(if (card.prefs.packGrammar) it.header else it.word) } ?: Ink.escape(w)
                val freq = Facts.frequency(card, rank)?.let { "<br><small>${Ink.escape(it)}</small>" }.orEmpty()
                listRow(p, "<b>$head</b>$freq") { card.lookUp(h?.word ?: w) }
            }
            card.scrollBack()
        }
    }

    /** Full articles: dictionary [i] of [hits] on the page, ◀ ▶ (or a swipe) to the others, in the person's order. */
    private fun pages(hits: List<Pair<StarDict, List<Hit>>>, word: String, i: Int, to: (() -> Unit)?) {
        val p = page ?: return
        p.removeAllViews(); up(to)
        turn = { d -> if (i + d in hits.indices) pages(hits, word, i + d, to) }
        place = "p:$i"
        val (dict, list) = hits[i]
        val bar = WordsList.bar(card, Ink.escape((if (hits.size > 1) "${i + 1} / ${hits.size} · " else "") + card.source(dict)), card.size * 0.72f, i, hits.size) { j -> pages(hits, word, j, to) }
        p.addView(bar, Ink.lp().apply { bottomMargin = Ink.dp(card, 8) })
        entries(p, list, word)
        sections(p, dict, list.first().word)
    }

    /** The entries, each under its headword when that is another form than [word]. */
    private fun entries(p: LinearLayout, list: List<Hit>, word: String) {
        list.forEach { h -> p.addView(Ink.text(card, (if (h.word.equals(word, true)) "" else "<b>${Ink.escape(h.word)}</b><br>") + h.html, card.size, html = true)
            .also { TextActions.selectable(it) }) }
        card.scrollTop()
    }

    /** A dictionary's full entries for [word]; "←" (or Back) returns to the list. */
    private fun article(dict: StarDict, list: List<Hit>, word: String, to: () -> Unit) {
        val p = page ?: return
        job?.cancel(); p.removeAllViews(); up(to); turn = null; place = "a:${dict.id}"
        p.addView(Ink.text(card, card.source(dict), card.size * 0.72f, bold = true).also { TextActions.selectable(it) }, Ink.lp().apply { bottomMargin = Ink.dp(card, 8) })
        entries(p, list, word)
        sections(p, dict, list.first().word)
    }

    private fun gloss() {
        val p = page ?: return
        job?.cancel(); p.removeAllViews(); up(null); turn = null
        // A word opens as a card of its own: its header, its ☆; ← comes back to this list.
        job = card.launch {
            val study = withContext(Dispatchers.IO) { card.study }
            if (study == null) { job = rows(p) { w -> card.lookUp(w) }; return@launch }
            // The language you learn: word by word through its pack.
            val lib = card.prefs.library()
            val g = withContext(Dispatchers.IO) { runCatching { lib.gloss(card.query, study) }.getOrDefault(emptyList()) }
            if (g.isEmpty()) card.note(p, card.getString(R.string.not_found))
            g.forEach { x -> listRow(p, glossLine(x)) { card.lookUp(x.base.ifBlank { x.word }) } }
            card.scrollBack()
        }
    }

    /** A gloss row: "schläft: schlafen (schläft, schlief, hat geschlafen) — спать", the word alone when no pack knows it. */
    private fun glossLine(x: Gloss): String {
        val line = x.head?.let { Ink.escape(if (card.prefs.packGrammar) it.line else "${it.word} — ${it.meaning}") }
        return when {
            line == null -> "<b>${Ink.escape(x.word)}</b>"
            x.base.equals(x.word, true) -> line
            else -> "<b>${Ink.escape(x.word)}</b>: $line"
        }
    }


    /** One row of a word list (the gloss, related words): its text, a thin line under it; a tap does [tap]. */
    private fun listRow(p: LinearLayout, html: String, tap: () -> Unit) {
        p.addView(Ink.opener(card, Ink.text(card, html, card.size, html = true).apply {
            val q = Ink.dp(card, 7); setPadding(0, q, 0, q); TextActions.selectable(this)
        }, tap))
        p.addView(Ink.divider(card))
    }

    /** Under a pack's article: a row per section it has (Card → Article) — a preview, its caption ›. */
    private fun sections(p: LinearLayout, dict: StarDict, word: String) {
        val e = runCatching { dict.related?.of(word) }.getOrNull() ?: return
        val prefs = card.prefs
        fun row(k: String, preview: String, caption: String) {
            if (!prefs.packSection(k) || preview.isBlank()) return
            p.addView(Ink.divider(card).apply { (layoutParams as LinearLayout.LayoutParams).topMargin = Ink.dp(card, 10) })
            p.addView(Ink.opener(card, Ink.vertical(card).apply {
                val q = Ink.dp(card, 7); setPadding(0, q, 0, q)
                addView(Ink.text(card, preview, card.size * 0.9f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
                addView(Ink.text(card, caption, card.size * 0.65f))
            }, { section(dict, word, k, 0) }))
        }
        fun few(xs: List<String>, sep: String = ", ") = xs.take(10).joinToString(sep) + if (xs.size > 10) " …" else ""
        row("rel", few(e.rel), "${card.getString(R.string.sec_rel)} · ${e.rel.size}")
        row("syn", few(e.syn) + if (e.ant.isEmpty()) "" else " ≠ " + few(e.ant), card.getString(R.string.sec_syn) + " · ${e.syn.size}" +
            if (e.ant.isEmpty()) "" else " · " + card.getString(R.string.sec_ant) + " ${e.ant.size}")
        row("expr", few(e.expr, "; "), "${card.getString(R.string.sec_expr)} · ${e.expr.size}")
        row("forms", few(e.forms.map { it.first }), card.getString(R.string.sec_forms) + if (e.ipa.isEmpty()) "" else " · " + e.ipa.joinToString(", "))
        row("ety", e.origin.take(120) + if (e.origin.length > 120) " …" else "", card.getString(R.string.sec_ety))
    }

    /**
     * A section of [word] in [dict] on the page: words with their first meaning from the same dictionary, 20 per page with
     * ◀ ▶ (a tap looks one up; ← returns here); forms and origin as text. "←" goes back to the article.
     */
    private fun section(dict: StarDict, word: String, k: String, n: Int) {
        val p = page ?: return
        val e = runCatching { dict.related?.of(word) }.getOrNull() ?: return
        // ← returns to the view it came from: the article, or the full articles at that dictionary (◀ ▶ kept).
        val from = place.takeUnless { it.startsWith("r:") } ?: "a:${dict.id}"
        job?.cancel(); p.removeAllViews(); turn = null; place = "r:${dict.id}:$k:$n:$word"; card.scrollTop()
        up { restore = from; articles(card.query, null) }
        val title = card.getString(when (k) { "rel" -> R.string.sec_rel; "syn" -> R.string.sec_syn; "expr" -> R.string.sec_expr; "forms" -> R.string.sec_forms; else -> R.string.sec_ety })
        val head = "${Ink.escape(word)} · ${Ink.escape(title)}"
        if (k == "forms" || k == "ety") p.addView(Ink.text(card, head, card.size * 0.8f, bold = true), Ink.lp().apply { bottomMargin = Ink.dp(card, 6) })
        when (k) {
            "forms" -> {
                if (e.ipa.isNotEmpty()) p.addView(Ink.text(card, e.ipa.joinToString(", "), card.size).also { TextActions.selectable(it) })
                e.forms.forEach { (f, tags) -> listRow(p, "<b>${Ink.escape(f)}</b> <small>${Ink.escape(tags)}</small>") { card.lookUp(f) } }
            }
            "ety" -> p.addView(Ink.text(card, e.origin, card.size).also { TextActions.selectable(it) })
            else -> {
                val all = when (k) { "rel" -> e.rel; "syn" -> e.syn + e.ant.map { "≠ $it" }; else -> e.expr }
                val pages = (all.size + WordsList.PAGE_SHORT - 1) / WordsList.PAGE_SHORT
                val at = n.coerceIn(0, (pages - 1).coerceAtLeast(0))
                val slice = all.drop(at * WordsList.PAGE_SHORT).take(WordsList.PAGE_SHORT)
                // ◀ title and "21–40 of 63" ▶ on top, as the dictionaries page; a swipe turns too.
                if (pages > 1) turn = { d -> if (at + d in 0 until pages) section(dict, word, k, at + d) }
                val bar = WordsList.bar(card, head + if (pages > 1) "<br>" + Ink.escape(card.getString(R.string.page_of, at * WordsList.PAGE_SHORT + 1,
                    minOf(all.size, (at + 1) * WordsList.PAGE_SHORT), all.size)) else "", card.size * 0.72f, at, pages) { j -> section(dict, word, k, j) }
                p.addView(bar, Ink.lp().apply { bottomMargin = Ink.dp(card, 8) })
                val box = Ink.vertical(card); p.addView(box)
                job = card.launch {
                    // The first meaning of each word from the same pack, off the main thread.
                    val lines = withContext(Dispatchers.IO) { slice.map { x -> val w = x.removePrefix("≠ ")
                        x to if (k == "expr") "" else runCatching { dict.lookup(w).let { hs -> hs.firstOrNull { it.word == w } ?: hs.firstOrNull { it.word.equals(w, true) } }?.let { meaning(it) } }.getOrNull().orEmpty() } }
                    lines.forEach { (x, m) -> val w = x.removePrefix("≠ ")
                        listRow(box, "<b>${Ink.escape(x)}</b>" + if (m.isBlank()) "" else " — " + Ink.escape(m)) { card.lookUp(w) } }
                    card.scrollBack()
                }
            }
        }
        if (k == "forms" || k == "ety") card.scrollBack()
    }

    /** Word by word from the packs, offline: base form and first meanings; [tap] opens a word. */
    private fun rows(p: LinearLayout, tap: (String) -> Unit): Job {
        val wait = Ink.text(card, "…", card.size); p.addView(wait)
        return card.launch {
            val t0 = System.currentTimeMillis()
            val (g, apart) = warm().await()
            p.removeView(wait)
            android.util.Log.i("InkPeek", "gloss ${g.size} words, ${apart.size} apart: ${System.currentTimeMillis() - t0} ms")
            card.note(p, card.getString(R.string.gloss_title))
            if (g.isEmpty()) card.note(p, card.getString(R.string.not_found))
            // A verb written apart first: "mache … zu: zumachen (macht zu, …) — закрывать".
            apart.forEach { (f, h) ->
                val line = h?.let { Ink.escape(if (card.prefs.packGrammar) it.line else "${it.word} — ${it.meaning}") } ?: "<b>${Ink.escape(f.verb)}</b>"
                listRow(p, "<b>${Ink.escape(f.form)} … ${Ink.escape(f.particle)}</b>: $line") { tap(f.verb) }
            }
            // The same first line as the header and the Dictionary tab: "der Hund (Hundes, мн. Hunde) — собака".
            g.forEach { x -> listRow(p, glossLine(x)) { tap(x.base.ifBlank { x.word }) } }
            card.scrollBack() // ← from a word: where the list was left
        }
    }
}
