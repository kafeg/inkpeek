package io.github.kafeg.inkpeek.ui

import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.ai.Ask
import io.github.kafeg.inkpeek.ai.Kind
import io.github.kafeg.inkpeek.mt.Engine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Translate: by AI when it can answer (on, set up, online) — its tab first; else offline on the device (through English
 * when neither side is English), always there as the other tab.
 */
class TranslatePage(private val card: CardActivity) {
    private var by = ""
    private var job: Job? = null

    fun fill(p: LinearLayout) {
        job?.cancel(); p.removeAllViews(); card.hint(CardActivity.TRANSLATE, null)
        job = card.launch {
            val aiOk = card.prefs.aiOn && withContext(Dispatchers.IO) { card.answers.block(Ask.QUICK) } == null
            if (by.isEmpty()) by = if (aiOk) "ai" else "offline"
            if (card.prefs.aiOn) {
                val tabs = listOf("ai" to "AI", "offline" to card.getString(R.string.mt_offline)).let { if (aiOk) it else it.reversed() }
                val seg = Ink.subTabs(card, tabs, by, card.size * 0.75f) { k ->
                    if (k == "ai" && !aiOk) card.launch { withContext(Dispatchers.IO) { card.answers.block(Ask.QUICK) }?.let { card.toast(card.getString(it)) } }
                    else { by = k; fill(p) }
                }
                if (!aiOk) seg.findViewWithTag<View>("ai")?.alpha = 0.35f
                p.addView(seg, Ink.lp().apply { bottomMargin = Ink.dp(card, 8) })
            }
            val out = Ink.text(card, "", card.size); p.addView(out)
            if (by == "ai" && aiOk) with(card.ai) { await(out, Ask.QUICK) }.fold(
                { card.markdown(out, card.ai.render(CardActivity.TRANSLATE, it)) }, { out.text = aiError(card, card.prefs, card.online, it) })
            else offline(p, out)
        }
    }

    private suspend fun CoroutineScope.offline(p: LinearLayout, out: TextView) {
        val d = card.mt.start(card)
        if (!d.isCompleted) out.text = card.getString(R.string.mt_working)
        val r = d.await(); val to = card.prefs.answerCode; val locale = card.resources.configuration.locales[0]
        val text = r.text
        if (text == null) {
            out.text = r.other?.let { card.getString(R.string.lang_unsupported, Locale(it).getDisplayLanguage(locale)) } ?: card.getString(R.string.mt_missing)
            if (card.kind != Kind.WORD) card.dictionary.under(p, this)
            return
        }
        // Which language and how, in small print at the card's bottom: the translation alone in the page.
        text.fold({ out.text = it; val from = r.from
            if (from != null && from != to) card.hint(CardActivity.TRANSLATE, Locale(from).getDisplayLanguage(locale).replaceFirstChar { c -> c.uppercase() } +
                if (Engine.steps(from, to).size > 1) " · " + card.getString(R.string.mt_via_english) else "") },
            { out.text = it.message ?: it.javaClass.simpleName })
    }
}
