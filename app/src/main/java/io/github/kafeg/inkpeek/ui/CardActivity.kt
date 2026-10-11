package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.ai.Action
import io.github.kafeg.inkpeek.ai.Provider
import io.github.kafeg.inkpeek.ai.Ai
import io.github.kafeg.inkpeek.ai.Kind
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
    private var shown = ""
    /** Header button "←" next to "✕": shown while a word's article is open from the gloss. */
    private lateinit var backButton: android.widget.ImageView
    private lateinit var titleView: TextView
    private var dictBack: (() -> Unit)? = null
    private var query = ""
    private val kind get() = Kind.of(query)
    /** Always: a word → its articles; a phrase or passage → a word-by-word gloss. */
    private val dictTab get() = true
    private val online: Boolean get() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
    /** Enabled AI tabs that fit this selection. */
    private val actions get() = if (prefs.aiOn) prefs.actions.filter { kind in it.kinds } else emptyList()
    private val size get() = prefs.fontSp.toFloat()

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Ink.localized(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        query = cleanQuery(textOf(intent).orEmpty()).take(4000) // about a page
        if (query.isEmpty()) { finish(); return }
        build()
        placeWindow()
        val first = actions.firstOrNull()
        open(if ((kind != Kind.WORD && prefs.aiOn && prefs.phraseAutoAi && first != null) || !dictTab) first?.id ?: DICT else DICT)
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
        // Title over the full width, the close button pinned top-right (no weights: multi-line ellipsis measures right).
        val head = android.widget.FrameLayout(this)
        val x = Ink.dp(this, 44)
        val long = query.length > 40 // long selections: smaller, 2 lines; the whole text still goes to the lookup and AI
        titleView = Ink.text(this, query, if (long) size * 0.95f else size * 1.3f, bold = !long).apply {
            maxLines = if (long) 2 else 3; ellipsize = android.text.TextUtils.TruncateAt.END; setLineSpacing(0f, 1f)
            setPadding(0, 0, x + Ink.dp(this@CardActivity, 8), 0)
        }
        head.addView(titleView, android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        backButton = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_back); background = Ink.border(this@CardActivity)
            val q = Ink.dp(this@CardActivity, 9); setPadding(q, q, q, q)
            contentDescription = getString(R.string.back); visibility = View.GONE
            setOnClickListener { dictBack?.invoke() }
        }
        head.addView(backButton, android.widget.FrameLayout.LayoutParams(x, x, Gravity.TOP or Gravity.END).apply { marginEnd = x + Ink.dp(this@CardActivity, 8) })
        head.addView(Ink.button(this, "✕", size) { finish() }.apply { setPadding(0, 0, 0, 0) },
            android.widget.FrameLayout.LayoutParams(x, x, Gravity.TOP or Gravity.END))
        root.addView(head)
        scroll = ScrollView(this)
        root.addView(scroll, Ink.lp(h = 0, weight = 1f).apply { topMargin = Ink.dp(this@CardActivity, 8) })
        val tabs = (if (dictTab) listOf(DICT to getString(R.string.tab_dictionary)) else emptyList()) +
            actions.map { it.id to Ink.label(this, it) }
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
        if (prefs.aiOn && !online) root.addView(Ink.text(this, getString(R.string.offline), size * 0.65f),
            Ink.lp().apply { topMargin = Ink.dp(this@CardActivity, 6) })
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
        shown = tab; showBack()
    }

    private fun showBack() {
        val on = shown == DICT && dictBack != null
        backButton.visibility = if (on) View.VISIBLE else View.GONE
        val x = Ink.dp(this, 44); val gap = Ink.dp(this, 8)
        titleView.setPadding(0, 0, if (on) 2 * x + 2 * gap else x + gap, 0)
    }

    /** System Back from a word's article returns to the gloss first. */
    @Deprecated("Activity API") override fun onBackPressed() {
        if (shown == DICT && dictBack != null) dictBack?.invoke() else @Suppress("DEPRECATION") super.onBackPressed()
    }

    private fun note(p: LinearLayout, s: String) = p.addView(Ink.text(this, s, size * 0.85f))

    /** Script of the answer language: dictionaries whose meanings use it come first (de→ru before de→en). */
    private val answerScript: Character.UnicodeScript get() = when (prefs.answerLang) {
        "Russian", "Ukrainian", "Bulgarian", "Serbian", "Belarusian", "Kazakh" -> Character.UnicodeScript.CYRILLIC
        "Chinese", "Japanese" -> Character.UnicodeScript.HAN
        "Korean" -> Character.UnicodeScript.HANGUL
        "Arabic", "Persian" -> Character.UnicodeScript.ARABIC
        "Greek" -> Character.UnicodeScript.GREEK
        "Hebrew" -> Character.UnicodeScript.HEBREW
        "Hindi" -> Character.UnicodeScript.DEVANAGARI
        "Thai" -> Character.UnicodeScript.THAI
        else -> Character.UnicodeScript.LATIN
    }

    /** Dictionary tab: a word → its articles; a phrase or passage → word-by-word gloss, tap a word for its articles. */
    private fun fillDictionary(p: LinearLayout) = if (kind == Kind.WORD) fillArticles(p, query, null) else fillGloss(p)

    /** One dictionary per screen; ◀ ▶ page between dictionaries that have [word]. [back] — return to the gloss. */
    private fun fillArticles(p: LinearLayout, word: String, back: (() -> Unit)?) {
        p.removeAllViews()
        dictBack = back?.let { b -> { dictBack = null; showBack(); b() } }
        showBack()
        val wait = Ink.text(this, "…", size); p.addView(wait)
        launch {
            val notReady = ArrayList<StarDict>()
            val lib = prefs.library()
            val res = withContext(Dispatchers.IO) { runCatching {
                lib.lookup(word, prefs.order, prefs.disabled, notReady)
                    .sortedBy { (_, h) -> if (lib.scriptOf(h.first().html.replace(Regex("<[^>]+>"), " ")) == answerScript) 0 else 1 }
            } }
            p.removeView(wait)
            val hits = res.getOrElse { note(p, getString(R.string.dict_error, it.message)); return@launch }
            if (notReady.isNotEmpty()) note(p, getString(R.string.preparing, notReady.joinToString { it.name }))
            if (hits.isEmpty()) {
                if (notReady.isEmpty()) note(p, getString(if (lib.all().isEmpty()) R.string.no_dicts_card else R.string.not_found))
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
                    val head = if (h.word.equals(word, true)) "" else "<b>${h.word}</b><br>"
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

    private var glossCache: List<io.github.kafeg.inkpeek.stardict.Gloss>? = null

    /** Word by word from the local dictionaries (works offline); tap a line for the word's articles. */
    private fun fillGloss(p: LinearLayout) {
        p.removeAllViews()
        val wait = Ink.text(this, "…", size); p.addView(wait)
        launch {
            val g = glossCache ?: withContext(Dispatchers.IO) {
                runCatching { prefs.library().gloss(query, prefs.order, prefs.disabled, answerScript) }.getOrDefault(emptyList())
            }.also { glossCache = it }
            p.removeView(wait)
            note(p, getString(R.string.gloss_title))
            if (g.isEmpty()) { note(p, getString(R.string.not_found)); return@launch }
            g.forEach { x ->
                val base = if (x.base.equals(x.word, true)) "" else " → ${Ink.escape(x.base)}"
                val meaning = if (x.meaning.isBlank()) "" else " — ${Ink.escape(x.meaning)}"
                val row = Ink.text(this@CardActivity, "<b>${Ink.escape(x.word)}</b>$base$meaning", size, html = true).apply {
                    val q = Ink.dp(this@CardActivity, 4); setPadding(0, q, 0, q)
                    setOnClickListener { fillArticles(p, x.base.ifBlank { x.word }) { fillGloss(p) } }
                }
                p.addView(row)
            }
        }
    }

    /** A short, actionable error instead of an exception name. */
    private fun friendly(e: Throwable): String {
        val name = e.javaClass.simpleName; val msg = e.message.orEmpty()
        val net = e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException ||
            e.cause is java.net.UnknownHostException || e is java.io.InterruptedIOException
        // Own server: online but it does not answer, or no address yet.
        if (prefs.provider == Provider.CUSTOM && ((net && online) || msg.startsWith("No server address")))
            return getString(R.string.err_server, prefs.url(Provider.CUSTOM).ifBlank { "—" }) + if (net) "\n\n($name)" else ""
        return getString(when {
            e is java.net.UnknownHostException || e is java.net.ConnectException || e is java.net.SocketTimeoutException ||
                e.cause is java.net.UnknownHostException || !online -> R.string.err_offline
            name.contains("Unauthorized") || msg.contains("HTTP 401") || name.contains("PermissionDenied") || msg.contains("HTTP 403") -> R.string.err_key
            name.contains("RateLimit") || msg.contains("HTTP 429") -> R.string.err_rate
            msg.contains("HTTP 402") || msg.contains("credit", true) -> R.string.err_credits
            msg.startsWith("No API key") -> R.string.err_no_key
            msg.startsWith("No model") -> R.string.err_no_model
            else -> R.string.err_other
        }).let { t -> if (t == getString(R.string.err_other)) "$t\n\n($name${if (msg.isNotBlank()) ": " + msg.take(160) else ""})" else t }
    }

    private fun fillAi(p: LinearLayout, a: Action) {
        Ai.cached(a, query)?.let { p.addView(Ink.text(this, Ink.markdown(it), size, html = true)); return }
        val out = Ink.text(this, "", size); p.addView(out)
        val pr = prefs.provider
        val dots = launch { var n = 0; while (true) { out.text = getString(R.string.thinking) + ".".repeat(n % 3 + 1); n++; delay(700) } }
        launch {
            val r = withContext(Dispatchers.IO) { runCatching { Ai.ask(pr, prefs.effectiveKey(pr), prefs.model(pr), a, query, prefs.answerLang, prefs.url(pr)) } }
            dots.cancel()
            r.fold({ out.text = android.text.Html.fromHtml(Ink.markdown(it), android.text.Html.FROM_HTML_MODE_COMPACT) },
                { out.text = friendly(it) })
            if (r.isFailure) pages.remove(a.id) // ask again next time
        }
    }

    companion object { const val DICT = "dict" }
}
