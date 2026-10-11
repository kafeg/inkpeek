package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.ai.Action
import io.github.kafeg.inkpeek.ai.Ai
import io.github.kafeg.inkpeek.stardict.StarDict
import io.github.kafeg.inkpeek.stardict.cleanQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The pop-up card. Tabs at the bottom: Dictionary first, then one tab per enabled AI action.
 * Each tab keeps its content; an AI answer is asked once per tab and shown again on return.
 */
class CardActivity : Activity(), CoroutineScope by MainScope() {
    private lateinit var prefs: Prefs
    private lateinit var scroll: ScrollView
    private val pages = HashMap<String, LinearLayout>()
    private val tabButtons = LinkedHashMap<String, TextView>()
    private var query = ""
    private val size get() = prefs.fontSp.toFloat()

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Ink.localized(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        query = cleanQuery(textOf(intent).orEmpty()).take(500)
        if (query.isEmpty()) { finish(); return }
        build()
        placeWindow()
        val first = prefs.actions.firstOrNull()
        open(if (query.contains(' ') && prefs.aiOn && prefs.phraseAutoAi && first != null) first.id else DICT)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); recreate() }
    override fun onDestroy() { cancel(); super.onDestroy() }

    private fun textOf(i: Intent): String? = when (i.action) {
        "colordict.intent.action.SEARCH" -> i.getStringExtra("EXTRA_QUERY")
        Intent.ACTION_PROCESS_TEXT -> i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
        else -> i.getStringExtra("EXTRA_QUERY") ?: i.getStringExtra(Intent.EXTRA_TEXT)
    }

    private fun build() {
        val pad = Ink.dp(this, 14)
        val root = Ink.vertical(this).apply { background = Ink.border(this@CardActivity, 3, 10); setPadding(pad, pad, pad, pad) }
        val head = Ink.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        head.addView(Ink.text(this, query, size * 1.3f, bold = true), Ink.lp(0, weight = 1f))
        val x = Ink.dp(this, 44)
        head.addView(Ink.button(this, "✕", size) { finish() }.apply { setPadding(0, 0, 0, 0) },
            LinearLayout.LayoutParams(x, x).apply { leftMargin = Ink.dp(this@CardActivity, 8) })
        root.addView(head)
        scroll = ScrollView(this)
        root.addView(scroll, Ink.lp(h = 0, weight = 1f).apply { topMargin = Ink.dp(this@CardActivity, 8) })
        val tabs = listOf(DICT to getString(R.string.tab_dictionary)) +
            if (prefs.aiOn) prefs.actions.map { it.id to Ink.label(this, it) } else emptyList()
        val row = Ink.horizontal(this)
        tabs.forEachIndexed { i, (id, label) ->
            val b = Ink.button(this, label, size * 0.72f) { open(id) }.apply {
                val q = Ink.dp(this@CardActivity, 4); setPadding(q, q * 3, q, q * 3); maxLines = 1
                setAutoSizeTextTypeUniformWithConfiguration(9, (size * 0.72f).toInt().coerceAtLeast(10), 1, android.util.TypedValue.COMPLEX_UNIT_SP)
            }
            tabButtons[id] = b
            row.addView(b, Ink.lp(0, weight = 1f).apply { if (i > 0) leftMargin = Ink.dp(this@CardActivity, 5) })
        }
        if (tabs.size > 1) root.addView(row, Ink.lp().apply { topMargin = Ink.dp(this@CardActivity, 10) })
        if (!prefs.aiOn) root.addView(Ink.text(this, getString(R.string.ai_off), size * 0.65f),
            Ink.lp().apply { topMargin = Ink.dp(this@CardActivity, 6) })
        setContentView(root)
    }

    private fun placeWindow() {
        val dm = resources.displayMetrics
        window.setLayout((dm.widthPixels * 0.92).toInt(), (dm.heightPixels * 0.8).toInt())
        window.setGravity(when (prefs.position) {
            "top" -> Gravity.TOP; "bottom" -> Gravity.BOTTOM
            "reader" -> intent.getIntExtra("EXTRA_GRAVITY", Gravity.CENTER)
            else -> Gravity.CENTER
        })
        if (prefs.dim) { window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); window.setDimAmount(0.25f) }
        else window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setWindowAnimations(0)
    }

    /** Shows the tab, filling it the first time. */
    private fun open(tab: String) {
        tabButtons.forEach { (t, b) -> b.background = Ink.border(this, if (t == tab) 7 else 2) }
        val page = pages[tab] ?: Ink.vertical(this).also { p ->
            pages[tab] = p
            if (tab == DICT) fillDictionary(p) else Action.ALL.firstOrNull { it.id == tab }?.let { fillAi(p, it) }
        }
        scroll.removeAllViews(); scroll.addView(page); scroll.scrollTo(0, 0)
    }

    private fun note(p: LinearLayout, s: String) = p.addView(Ink.text(this, s, size * 0.85f))

    /** One dictionary per screen; ◀ ▶ page between dictionaries that have the word. */
    private fun fillDictionary(p: LinearLayout) {
        val wait = Ink.text(this, "…", size); p.addView(wait)
        launch {
            val notReady = ArrayList<StarDict>()
            val res = withContext(Dispatchers.IO) { runCatching { prefs.library().lookup(query, prefs.order, prefs.disabled, notReady) } }
            p.removeView(wait)
            val hits = res.getOrElse { note(p, getString(R.string.dict_error, it.message)); return@launch }
            if (notReady.isNotEmpty()) note(p, getString(R.string.preparing, notReady.joinToString { it.name }))
            if (hits.isEmpty()) {
                if (notReady.isEmpty()) note(p, getString(if (prefs.library().all().isEmpty()) R.string.no_dicts_card else R.string.not_found))
                return@launch
            }
            val nav = Ink.horizontal(this@CardActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            val title = Ink.text(this@CardActivity, "", size * 0.72f, bold = true).apply { gravity = Gravity.CENTER }
            val body = Ink.vertical(this@CardActivity)
            var at = 0
            fun show() {
                val (dict, list) = hits[at]
                title.text = if (hits.size > 1) "${dict.name} · ${at + 1}/${hits.size}" else dict.name
                body.removeAllViews()
                list.forEach { h ->
                    val head = if (h.word.equals(query, true)) "" else "<b>${h.word}</b><br>"
                    body.addView(Ink.text(this@CardActivity, head + h.html, size, html = true))
                }
                scroll.scrollTo(0, 0)
            }
            val w = Ink.dp(this@CardActivity, 48)
            if (hits.size > 1) nav.addView(Ink.button(this@CardActivity, "◀", size) { at = (at - 1 + hits.size) % hits.size; show() }
                .apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(w, w))
            nav.addView(title, Ink.lp(0, weight = 1f))
            if (hits.size > 1) nav.addView(Ink.button(this@CardActivity, "▶", size) { at = (at + 1) % hits.size; show() }
                .apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(w, w))
            p.addView(nav, Ink.lp().apply { bottomMargin = Ink.dp(this@CardActivity, 8) })
            p.addView(body)
            show()
        }
    }

    private fun fillAi(p: LinearLayout, a: Action) {
        Ai.cached(a, query)?.let { p.addView(Ink.text(this, Ink.markdown(it), size, html = true)); return }
        val out = Ink.text(this, "", size); p.addView(out)
        val pr = prefs.provider
        val dots = launch { var n = 0; while (true) { out.text = getString(R.string.thinking) + ".".repeat(n % 3 + 1); n++; delay(700) } }
        launch {
            val r = withContext(Dispatchers.IO) { runCatching { Ai.ask(pr, prefs.effectiveKey(pr), prefs.model(pr), a, query, prefs.answerLang) } }
            dots.cancel()
            r.fold({ out.text = android.text.Html.fromHtml(Ink.markdown(it), android.text.Html.FROM_HTML_MODE_COMPACT) },
                { out.text = "${it.message ?: it.javaClass.simpleName}\n\n" + getString(R.string.check_ai) })
            if (r.isFailure) pages.remove(a.id) // ask again next time
        }
    }

    companion object { const val DICT = "dict" }
}
