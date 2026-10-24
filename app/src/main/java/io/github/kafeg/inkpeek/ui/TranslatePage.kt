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
 * Translate: offline on the device first (through English when neither side is English), AI second and only when its
 * tab is tapped (or chosen as what a phrase opens on). Each tab only when it can be used: a pack's models, AI on.
 */
class TranslatePage(private val card: CardActivity) {
    var by = ""
    /** Opened by the card itself: a text with no offline translation goes on to Dictionary (word by word). */
    var auto = false
    private var job: Job? = null

    fun fill(p: LinearLayout) {
        job?.cancel(); p.removeAllViews(); card.hint(CardActivity.TRANSLATE, null)
        job = card.launch {
            val aiOn = card.prefs.aiOn
            val block = if (aiOn) withContext(Dispatchers.IO) { card.answers.block(Ask.QUICK) } else null
            val aiOk = aiOn && block == null
            if (by.isEmpty()) by = if (card.offlineOk) "offline" else "ai"
            if (aiOn && card.offlineOk) {
                val tabs = listOf("offline" to card.getString(R.string.mt_offline), "ai" to "AI")
                val seg = Ink.subTabs(card, tabs, by, card.size * 0.75f) { k ->
                    if (k == "ai" && !aiOk) block?.let { card.toast(card.getString(it)) }
                    else { by = k; fill(p) }
                }
                if (!aiOk) seg.findViewWithTag<View>("ai")?.alpha = Ink.OFF
                p.addView(seg, Ink.lp().apply { bottomMargin = Ink.dp(card, 8) })
            }
            val out = Ink.text(card, "", card.size).also { TextActions.selectable(it) }; p.addView(out)
            when {
                by == "ai" && aiOk -> with(card.ai) { await(out, Ask.QUICK) }.fold(
                    { card.markdown(out, card.ai.render(CardActivity.TRANSLATE, it)) }, { e ->
                        // The error, and a new try next time.
                        out.text = if (e is io.github.kafeg.inkpeek.ai.Answers.Offline) card.getString(R.string.err_offline) else aiError(card, card.prefs, card.online, e)
                        card.forget(CardActivity.TRANSLATE)
                    })
                // AI cannot answer now and there is no offline translation: say why.
                by == "ai" -> out.text = block?.let { card.getString(it) }.orEmpty()
                else -> offline(p, out)
            }
        }
    }

    private suspend fun CoroutineScope.offline(p: LinearLayout, out: TextView) {
        val d = card.mt.start(card)
        if (!d.isCompleted) out.text = card.getString(R.string.mt_working)
        val r = d.await(); val to = card.prefs.answerCode; val locale = card.resources.configuration.locales[0]
        val text = r.text
        if (text == null) {
            // Which InkPeek pack would translate it: the text's language (fastText) into the app's; or that there is none yet.
            val lang = r.other ?: r.from
            val offered = lang != null && withContext(Dispatchers.IO) { card.prefs.packs.cachedList() }?.let { io.github.kafeg.inkpeek.packs.Packs.pairs(it) }
                ?.contains(lang to to) == true
            out.text = Ink.arrows(card, when {
                lang != null && offered -> card.getString(R.string.lang_need_pack, Ink.lang(card, lang), Ink.lang(card, to))
                r.other != null -> card.getString(R.string.lang_unsupported, Locale(r.other).getDisplayLanguage(locale))
                else -> card.getString(R.string.mt_missing)
            }, out.textSize)
            // Opened by the card itself: no translation for this text, so its words in Dictionary instead.
            if (auto && card.kind != Kind.WORD) { auto = false; card.open(CardActivity.DICT) }
            return
        }
        // Which language and how, in small print at the card's bottom: the translation alone in the page.
        text.fold({ out.text = Ink.arrows(card, it, out.textSize); val from = r.from
            if (from != null && from != to) card.hint(CardActivity.TRANSLATE, Ink.lang(card, from) +
                // The model that translated: "German · OPUS-MT", or Mozilla's "German · through English".
                if (r.by.isNotEmpty()) " · " + r.by else if (Engine.steps(from, to).size > 1) " · " + card.getString(R.string.mt_via_english) else "") },
            { out.text = it.message ?: it.javaClass.simpleName })
    }
}
