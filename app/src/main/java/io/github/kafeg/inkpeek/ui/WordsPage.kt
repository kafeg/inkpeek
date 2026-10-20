package io.github.kafeg.inkpeek.ui

import android.widget.LinearLayout
import io.github.kafeg.inkpeek.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** My words in the card: saved words or phrases; tap opens one in the card, ✕ removes it. */
class WordsPage(private val card: CardActivity) {
    private var kind = "word"
    private var job: Job? = null

    fun fill(p: LinearLayout) {
        job?.cancel(); p.removeAllViews()
        job = card.launch {
            val lang = card.prefs.answerLang; val now = kind; val store = card.store
            val (items, counts) = withContext(Dispatchers.IO) { runCatching {
                store.saved(now, lang) to listOf(store.count("saved", "word"), store.count("saved", "phrase"))
            }.getOrNull() } ?: return@launch
            p.addView(Ink.subTabs(card, listOf("word" to card.getString(R.string.list_words) + " (" + counts[0] + ")",
                "phrase" to card.getString(R.string.list_phrases) + " (" + counts[1] + ")"), now, card.size * 0.75f) { kind = it; fill(p) },
                Ink.lp().apply { bottomMargin = Ink.dp(card, 8) })
            if (items.isEmpty()) card.note(p, card.getString(R.string.list_empty_saved))
            items.forEach { e ->
                p.addView(WordsList.row(card, e, card.size * 0.85f, { card.startActivity(WordsList.card(card, e.text)) }) {
                    // The activity's scope: this coroutine is over by the time of the tap.
                    card.launch {
                        withContext(Dispatchers.IO) { runCatching { store.unsave(e.text) } }
                        if (e.text == card.query) card.starred(false)
                        fill(p)
                    }
                })
            }
        }
    }
}
