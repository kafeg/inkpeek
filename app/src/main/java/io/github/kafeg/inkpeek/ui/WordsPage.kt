package io.github.kafeg.inkpeek.ui

import android.widget.LinearLayout
import io.github.kafeg.inkpeek.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Notes in the card: saved words or phrases; tap opens one in the card, ✕ removes it. */
class WordsPage(private val card: CardActivity) {
    private var kind = "word"
    private var page = 0
    private var job: Job? = null

    fun fill(p: LinearLayout) {
        job?.cancel(); p.removeAllViews()
        job = card.launch {
            val lang = card.prefs.answerLang; val now = kind; val store = card.store
            val (items, counts) = withContext(Dispatchers.IO) { runCatching {
                store.saved(now, lang) to listOf(store.count("saved", "word"), store.count("saved", "phrase"))
            }.getOrNull() } ?: return@launch
            p.addView(Ink.subTabs(card, listOf("word" to card.getString(R.string.list_words) + " (" + counts[0] + ")",
                "phrase" to card.getString(R.string.list_phrases) + " (" + counts[1] + ")"), now, card.size * 0.75f) { kind = it; page = 0; fill(p) },
                Ink.lp().apply { bottomMargin = Ink.dp(card, 8) })
            if (items.isEmpty()) card.note(p, card.getString(R.string.list_empty_saved))
            // 50 per page, the pager over them (as the dictionaries page).
            val pages = (items.size + WordsList.PAGE - 1) / WordsList.PAGE
            page = page.coerceIn(0, (pages - 1).coerceAtLeast(0))
            if (pages > 1) p.addView(WordsList.pager(card, page, items.size, card.size * 0.75f) { page = it; fill(p); card.scrollTop() },
                Ink.lp().apply { bottomMargin = Ink.dp(card, 6) })
            items.drop(page * WordsList.PAGE).take(WordsList.PAGE).forEach { e ->
                p.addView(WordsList.row(card, e, card.size * 0.85f, { card.lookUp(e.text) }) {
                    // The activity's scope: this coroutine is over by the time of the tap.
                    Rows.confirm(card, card.getString(R.string.unsave_q, e.text)) { card.launch {
                        withContext(Dispatchers.IO) { runCatching { store.unsave(e.text) } }
                        if (e.text == card.query) card.starred(false)
                        fill(p)
                    } }
                })
            }
        }
    }
}
