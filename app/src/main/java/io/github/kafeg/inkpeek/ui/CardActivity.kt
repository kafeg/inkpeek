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
import io.github.kafeg.inkpeek.Store
import io.github.kafeg.inkpeek.ai.Action
import io.github.kafeg.inkpeek.ai.Provider
import io.github.kafeg.inkpeek.ai.Ai
import io.github.kafeg.inkpeek.ai.Ask
import io.github.kafeg.inkpeek.mt.Engine
import io.github.kafeg.inkpeek.ai.Kind
import io.github.kafeg.inkpeek.stardict.StarDict
import io.github.kafeg.inkpeek.stardict.cleanPhrase
import io.github.kafeg.inkpeek.stardict.cleanQuery
import io.github.kafeg.inkpeek.stardict.shortMeaning
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The pop-up card. Tabs at the bottom: Dictionary, then Translate, Analysis, Context. On open one quick AI answer
 * (head facts + translation) fills the header and Translate; Analysis and Context share one detailed answer.
 */
class CardActivity : Activity(), CoroutineScope by MainScope() {
    private lateinit var prefs: Prefs
    private lateinit var scroll: ScrollView
    private val pages = HashMap<String, LinearLayout>()
    private val tabButtons = LinkedHashMap<String, View>()
    private var shown = ""
    /** Header button "←": shown while a word's article is open from the gloss. */
    private lateinit var backButton: android.widget.ImageView
    private lateinit var starButton: android.widget.ImageView
    private val store by lazy { Store.get(this) }
    private val kindName get() = if (kind == Kind.WORD) "word" else "phrase"
    private lateinit var titleView: TextView
    private lateinit var subView: TextView
    private lateinit var titleBox: LinearLayout
    /** The two AI answers, started once and shared by the tabs; a failed one is asked again on the next tab open. */
    private val answers = HashMap<Ask, Deferred<Result<org.json.JSONObject>>>()
    private var dictBack: (() -> Unit)? = null
    private var query = ""
    private val kind get() = Kind.of(query)
    /** Always: a word → its articles; a phrase or passage → a word-by-word gloss. */
    private val dictTab get() = true
    private val online: Boolean get() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
        val active = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        if (!active.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return false
        // A VPN stays "validated" after Wi-Fi goes off: it needs a real network under it.
        @Suppress("DEPRECATION") return active.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN) || cm.allNetworks.any { n ->
            cm.getNetworkCapabilities(n)?.let { it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                it.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET) } == true
        }
    }
    private val actions get() = if (prefs.aiOn) Action.ALL else emptyList()
    private val size get() = prefs.fontSp.toFloat()

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Ink.localized(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        // A word loses its edge punctuation; a phrase keeps "?" or "!" — the sentence type is part of the meaning.
        val raw = textOf(intent).orEmpty().take(4000) // about a page
        query = if (Kind.of(cleanQuery(raw)) == Kind.WORD) cleanQuery(raw) else cleanPhrase(raw)
        Ai.recall = { prefs.variant(it) }; Ai.remember = { k, v -> prefs.setVariant(k, v) }
        launch(Dispatchers.IO) { runCatching { store.seen(query, kindName) } }
        if (query.isEmpty()) { finish(); return }
        build()
        placeWindow()
        if (kind == Kind.WORD) localHead()
        // The core opens; an AI tab only when the user asked for it in settings and AI can answer.
        open(if (kind != Kind.WORD && prefs.phraseAutoAi) TRANSLATE else DICT)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); recreate() }
    override fun onDestroy() { cancel(); super.onDestroy() }

    private fun textOf(i: Intent): String? = when (i.action) {
        "colordict.intent.action.SEARCH" -> i.getStringExtra("EXTRA_QUERY")
        Intent.ACTION_PROCESS_TEXT -> i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
        else -> i.getStringExtra("EXTRA_QUERY") ?: i.getStringExtra(Intent.EXTRA_TEXT)
    }

    /** The card (header, body) with the icon bar under it, outside its border: tabs · save, my words · close. */
    private fun build() {
        val pad = Ink.dp(this, 14); val x = Ink.dp(this, 44)
        val card = Ink.vertical(this).apply { background = Ink.border(this@CardActivity, 3, 10); setPadding(pad, pad, pad, pad) }
        val head = android.widget.FrameLayout(this)
        val long = query.length > 40 // long selections: smaller, 2 lines; the whole text still goes to the lookup and AI
        titleView = Ink.text(this, query, if (long) size * 0.95f else size * 1.3f, bold = !long).apply {
            maxLines = if (long) 2 else 3; ellipsize = android.text.TextUtils.TruncateAt.END; setLineSpacing(0f, 1f)
        }
        // Under the title: the selected form, part of speech, "irregular" — once the quick answer is in.
        subView = Ink.text(this, "", size * 0.72f).apply { visibility = View.GONE }
        titleBox = Ink.vertical(this).apply { addView(titleView); addView(subView) }
        head.addView(titleBox, android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        backButton = android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_back); background = Ink.border(this@CardActivity)
            val q = Ink.dp(this@CardActivity, 9); setPadding(q, q, q, q)
            contentDescription = getString(R.string.back); tooltipText = contentDescription; visibility = View.GONE
            setOnClickListener { dictBack?.invoke() }
        }
        head.addView(backButton, android.widget.FrameLayout.LayoutParams(x, x, Gravity.TOP or Gravity.END))
        card.addView(head)
        scroll = ScrollView(this)
        card.addView(scroll, Ink.lp(h = 0, weight = 1f).apply { topMargin = Ink.dp(this@CardActivity, 8) })
        val hint = when { !prefs.aiOn -> getString(R.string.ai_off); !online -> getString(R.string.offline); else -> null }
        if (hint != null) card.addView(Ink.text(this, hint, size * 0.65f), Ink.lp().apply { topMargin = Ink.dp(this@CardActivity, 6) })

        val tabs = listOf(Triple(DICT, R.drawable.ic_dictionary, getString(R.string.tab_dictionary)),
            Triple(TRANSLATE, R.drawable.ic_translate, getString(R.string.tab_translate))) + actions.map { a ->
            Triple(a.id, if (a.id == "analysis") R.drawable.ic_analysis else R.drawable.ic_context, Ink.label(this, a))
        }
        val n = tabs.size + 3; val gap = Ink.dp(this, 5)
        val side = minOf(Ink.dp(this, 44), ((resources.displayMetrics.widthPixels * 0.92).toInt() - 2 * pad - gap * (n + 3)) / n)
        // The icon row closes the card, under a thin line: one frame, no dead space around it.
        val bar = Ink.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun add(v: View, first: Boolean = false) = bar.addView(v, Ink.lp(android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (!first) leftMargin = gap })
        tabs.forEachIndexed { i, (id, icon, label) ->
            val ai = id != DICT && id != TRANSLATE
            add(iconButton(icon, label, side, ai = ai) { if (ai) launch { val r = withContext(Dispatchers.IO) { aiBlock(id) }
                if (r != null) toast(getString(r)) else open(id) } else open(id) }.also { tabButtons[id] = it }, i == 0)
        }
        // AI tabs are dimmed while AI cannot answer; a saved answer still opens offline.
        launch { val blocked = withContext(Dispatchers.IO) { actions.associate { it.id to aiBlock(it.id) } }
            blocked.forEach { (id, r) -> tabButtons[id]?.alpha = if (r == null) 1f else 0.35f } }
        bar.addView(View(this), Ink.lp(gap * 2, 1))
        val star = iconButton(R.drawable.ic_star, getString(R.string.save_word), side, getString(R.string.save_short)) { toggleSaved() }
        starButton = star.findViewWithTag("icon"); add(star)
        add(iconButton(R.drawable.ic_words, getString(R.string.tab_words), side) { open(SAVED) }.also { tabButtons[SAVED] = it })
        bar.addView(View(this), Ink.lp(0, 1, weight = 1f))
        add(iconButton(R.drawable.ic_close, getString(R.string.close), side) { finish() })
        launch { if (withContext(Dispatchers.IO) { runCatching { store.isSaved(query) }.getOrDefault(false) }) starred(true) }

        card.addView(View(this).apply { setBackgroundColor(android.graphics.Color.BLACK) },
            Ink.lp(h = Ink.dp(this, 1)).apply { topMargin = Ink.dp(this@CardActivity, 8); bottomMargin = Ink.dp(this@CardActivity, 8) })
        card.addView(bar)
        setContentView(card)
    }

    /** A bordered icon; long press shows its name, the "captions" setting writes it under the icon. */
    private fun iconButton(icon: Int, label: String, side: Int, caption: String = label, ai: Boolean = false, click: () -> Unit): View {
        val q = side / 5
        // An AI icon is drawn a bit smaller: its top-right corner holds the "AI" label.
        val img = android.widget.ImageView(this).apply { setImageResource(icon); tag = "icon"
            if (ai) setPadding(q, q + q / 2, q + q / 2, q) else setPadding(q, q, q, q) }
        val name = if (ai) "$label · AI" else label
        return Ink.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL; background = Ink.border(this@CardActivity)
            contentDescription = name; tooltipText = name; isClickable = true; setOnClickListener { click() }
            // An AI tab: a small "AI" in the icon's corner.
            addView(if (!ai) img else android.widget.FrameLayout(this@CardActivity).apply {
                addView(img, android.widget.FrameLayout.LayoutParams(side, side))
                // Clear of the border even when thick (the open tab): 6 dp from the box corner.
                val m = Ink.dp(this@CardActivity, 6)
                addView(Ink.text(this@CardActivity, "AI", 9f, bold = true).apply { includeFontPadding = false },
                    android.widget.FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
                        .apply { topMargin = m; rightMargin = m })
            }, LinearLayout.LayoutParams(side, side))
            if (prefs.captions) addView(Ink.text(this@CardActivity, caption, 9f).apply {
                maxLines = 1; gravity = Gravity.CENTER; setPadding(2, 0, 2, Ink.dp(this@CardActivity, 3))
            }, LinearLayout.LayoutParams(side, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        }
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
        tabButtons.forEach { (t, b) -> b.background = Ink.border(this, if (t == tab) SELECTED else 2) }
        val page = pages[tab] ?: Ink.vertical(this).also { p ->
            pages[tab] = p
            when (tab) { DICT -> fillDictionary(p); SAVED -> fillSaved(p); TRANSLATE -> fillTranslate(p); else -> fillAi(p, tab) }
        }
        scroll.removeAllViews(); scroll.addView(page); scroll.scrollTo(0, 0)
        shown = tab; showBack()
    }

    private fun showBack() {
        val on = shown == DICT && dictBack != null
        backButton.visibility = if (on) View.VISIBLE else View.GONE
        val x = Ink.dp(this, 44); val gap = Ink.dp(this, 8)
        titleBox.setPadding(0, 0, if (on) x + gap else 0, 0)
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

    /** Dictionary tab: a word → one line per dictionary; a phrase or passage → word-by-word gloss. */
    private fun fillDictionary(p: LinearLayout) = if (kind == Kind.WORD) fillArticles(p, query, null) else fillGloss(p)

    /**
     * One short line per dictionary with [word] (form → base, part of speech, first meanings); tap a line for the full
     * article. Meanings in the answer script first, then the order from settings. [back] — return to the gloss.
     */
    private var listRun = 0

    private fun fillArticles(p: LinearLayout, word: String, back: (() -> Unit)?) {
        p.removeAllViews(); val run = ++listRun
        dictBack = back?.let { b -> { dictBack = null; showBack(); b() } }
        showBack()
        val wait = Ink.text(this, "…", size); p.addView(wait)
        launch {
            val notReady = ArrayList<StarDict>()
            val lib = prefs.library()
            val res = withContext(Dispatchers.IO) { runCatching {
                lib.lookup(word, prefs.order, prefs.disabled, notReady).sortedBy { (_, h) ->
                    lib.scriptOf(runCatching { shortMeaning(h.first().html, h.first().word) }.getOrDefault("")) != answerScript }
            } }
            if (run != listRun) return@launch // a newer fill took over (the quick answer re-sorted the list)
            p.removeView(wait)
            val hits = res.getOrElse { note(p, getString(R.string.dict_error, it.message)); return@launch }
            if (notReady.isNotEmpty()) note(p, getString(R.string.preparing, notReady.joinToString { it.name }))
            if (hits.isEmpty()) {
                if (notReady.isEmpty()) note(p, getString(if (lib.all().isEmpty()) R.string.no_dicts_card else R.string.not_found))
                return@launch
            }
            hits.forEach { (dict, list) ->
                val h = list.first()
                val form = if (h.word.equals(word, true)) "" else "${Ink.escape(word)} → "
                val gram = io.github.kafeg.inkpeek.stardict.grammarOf(h.html).let { if (it.isBlank()) "" else " <i>${Ink.escape(it)}</i>" }
                val meaning = runCatching { shortMeaning(h.html, h.word) }.getOrDefault("")
                val line = "$form<b>${Ink.escape(h.word)}</b>$gram" + (if (meaning.isBlank()) "" else " — " + Ink.escape(meaning)) +
                    "<br><small>${Ink.escape(dict.name)}</small>"
                p.addView(Ink.text(this@CardActivity, line, size, html = true).apply {
                    val q = Ink.dp(this@CardActivity, 6); setPadding(0, q, 0, q)
                    setOnClickListener { listRun++; article(p, dict, list, word) { fillArticles(p, word, back) } }
                })
            }
        }
    }

    /** A dictionary's full entries for [word]; "←" (or Back) returns to the list. */
    private fun article(p: LinearLayout, dict: StarDict, list: List<io.github.kafeg.inkpeek.stardict.Hit>, word: String, back: () -> Unit) {
        p.removeAllViews()
        dictBack = { dictBack = null; showBack(); back() }
        showBack()
        p.addView(Ink.text(this, dict.name, size * 0.72f, bold = true), Ink.lp().apply { bottomMargin = Ink.dp(this@CardActivity, 8) })
        list.forEach { h ->
            val head = if (h.word.equals(word, true)) "" else "<b>${h.word}</b><br>"
            p.addView(Ink.text(this, head + h.html, size, html = true))
        }
        scroll.scrollTo(0, 0)
    }

    private var glossCache: List<io.github.kafeg.inkpeek.stardict.Gloss>? = null

    /** Word by word from the local dictionaries (works offline); tap a line for the word's articles. */
    private fun fillGloss(p: LinearLayout) { p.removeAllViews(); glossInto(p) { w -> fillArticles(p, w) { fillGloss(p) } } }

    /** Adds the gloss rows to [p]; [tap] opens a word's articles. */
    private fun glossInto(p: LinearLayout, tap: (String) -> Unit) {
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
                    setOnClickListener { tap(x.base.ifBlank { x.word }) }
                }
                p.addView(row)
            }
        }
    }

    /** A short, actionable error instead of an exception name. */
    private fun friendly(e: Throwable): String {
        val chain = generateSequence(e) { it.cause }.take(8).toList()
        val name = e.javaClass.simpleName; val msg = chain.joinToString(" ") { it.message.orEmpty() }
        val net = chain.any { it is java.net.UnknownHostException || it is java.net.SocketException || it is java.net.SocketTimeoutException ||
            it is javax.net.ssl.SSLException || it is java.io.InterruptedIOException }
        val status = (e as? com.anthropic.errors.AnthropicServiceException)?.statusCode()
            ?: Regex("HTTP (\\d{3})").find(e.message.orEmpty())?.groupValues?.get(1)?.toInt() ?: 0
        // 403 or Gemini's 400 "User location is not supported": often a country block (VPN helps), sometimes the key.
        val region = status == 403 || Regex("(location|country|region|territory)[^.]{0,40}not supported|unsupported_country", RegexOption.IGNORE_CASE).containsMatchIn(msg)
        // Own server: online but it does not answer, or no address yet.
        if (prefs.provider == Provider.CUSTOM && ((net && online) || msg.startsWith("No server address")))
            return getString(R.string.err_server, prefs.url(Provider.CUSTOM).ifBlank { "—" }) + if (net) "\n\n($name)" else ""
        return getString(when {
            !online -> R.string.err_offline
            net -> R.string.err_unreachable
            region -> R.string.err_region
            status == 401 -> R.string.err_key
            status == 429 -> R.string.err_rate
            status == 402 || msg.contains("credit", true) -> R.string.err_credits
            msg.startsWith("No API key") -> R.string.err_no_key
            msg.startsWith("No model") -> R.string.err_no_model
            else -> R.string.err_other
        }).let { t -> if (t == getString(R.string.err_other) || t == getString(R.string.err_region)) "$t\n\n($name${if (e.message.isNullOrBlank()) "" else ": " + e.message!!.take(160)})" else t }
    }

    private class Offline : Exception()

    /** Why an AI tab cannot open now (string id), or null; a saved answer opens without a network. Blocking. */
    private fun aiBlock(tab: String = "translate"): Int? {
        val pr = prefs.provider
        return when {
            !prefs.aiOn -> R.string.ai_off
            pr != Provider.CUSTOM && prefs.effectiveKey(pr).isBlank() -> R.string.err_no_key
            prefs.model(pr).isBlank() -> R.string.err_no_model
            !online && runCatching { (if (tab == "translate") Ask.QUICK else Ask.DETAIL).let { store.answer(query, prefs.answerLang, it.section, it.version) } }.getOrNull() == null -> R.string.err_offline
            else -> null
        }
    }

    private fun toast(s: String) = android.widget.Toast.makeText(this, s, android.widget.Toast.LENGTH_LONG).show()

    /** The saved answer (also offline), else a request. */
    private fun answer(ask: Ask): Deferred<Result<org.json.JSONObject>> {
        answers[ask]?.let { d -> if (!d.isCompleted || d.getCompleted().isSuccess) return d }
        val pr = prefs.provider; val lang = prefs.answerLang; val model = prefs.model(pr)
        return async(Dispatchers.IO) { runCatching {
            store.answer(query, lang, ask.section, ask.version)?.let { return@runCatching org.json.JSONObject(it) }
            if (!online) throw Offline()
            Ai.ask(pr, prefs.effectiveKey(pr), model, ask, query, lang, prefs.url(pr))
                .also { runCatching { store.putAnswer(query, lang, ask.section, ask.version, model, it.toString()) } }
        } }.also { d ->
            answers[ask] = d
        }
    }

    private fun org.json.JSONObject.str(k: String) = optString(k).trim()

    /** Instant header from the dictionaries: a German noun's article from WikDict's grammar mark ("noun, neutral"). */
    private fun localHead() = launch {
        val lib = prefs.library()
        val (base, gram) = withContext(Dispatchers.IO) { runCatching {
            lib.lookup(query, prefs.order, prefs.disabled).firstNotNullOfOrNull { (d, hits) ->
                if (!Regex("\\(de-|Deutsch").containsMatchIn(d.name)) null
                else hits.firstNotNullOfOrNull { h -> io.github.kafeg.inkpeek.stardict.grammarOf(h.html, "noun").takeIf { it.startsWith("noun") }?.let { h.word to it } }
            }
        }.getOrNull() } ?: return@launch
        val article = when { "neut" in gram -> "das"; "fem" in gram -> "die"; "masc" in gram -> "der"; else -> return@launch }
        titleView.text = "$article $base"
        if (!base.equals(query, true)) { subView.text = "$query →"; subView.visibility = View.VISIBLE }
    }

    /** Markdown for a tab from its answer. */
    private fun render(tab: String, j: org.json.JSONObject): String = when (tab) {
        "translate" -> {
            // AI's own view of the word inside its tab: "das Haus · die Häuser", or the verb's forms, irregular.
            val facts = if (kind != Kind.WORD) "" else j.str("forms").ifBlank {
                listOf(j.str("article"), j.str("base")).filter { it.isNotBlank() }.joinToString(" ") + j.str("plural").let { if (it.isBlank()) "" else " · $it" }
            }.let { f -> listOf(f, j.str("pos"), j.str("form_of"), if (j.optBoolean("irregular")) getString(R.string.irregular) else "")
                .filter { it.isNotBlank() }.joinToString(" · ") }
            val m = j.optJSONArray("meanings")?.let { a -> (0 until a.length()).map { "- " + a.getString(it) } }.orEmpty()
            (listOf(if (facts.isBlank()) "" else "*$facts*", j.str("translation")) + m).filter { it.isNotBlank() }.joinToString("\n")
        }
        else -> j.str(tab)
    }

    /**
     * Translate (core): AI when the mode says so and it can answer, else the offline engine (through English when
     * neither side is English); a line under the text says which one answered.
     */
    private var translateBy = ""

    /** Two sources side by side, like My words: AI (when AI is on) and Offline; the setting picks the first one. */
    private fun fillTranslate(p: LinearLayout) {
        p.removeAllViews()
        launch {
            val aiOk = prefs.aiOn && withContext(Dispatchers.IO) { aiBlock(TRANSLATE) } == null
            if (translateBy.isEmpty()) translateBy = if (prefs.translateMode == "ai" && aiOk) "ai" else "offline"
            if (prefs.aiOn) {
                val seg = Ink.horizontal(this@CardActivity)
                listOf("ai" to "AI", "offline" to getString(R.string.mt_offline)).forEachIndexed { i, (k, label) ->
                    seg.addView(Ink.button(this@CardActivity, label, size * 0.75f) {
                        if (k == "ai" && !aiOk) launch { withContext(Dispatchers.IO) { aiBlock(TRANSLATE) }?.let { toast(getString(it)) } }
                        else { translateBy = k; fillTranslate(p) }
                    }.apply { background = Ink.border(this@CardActivity, if (k == translateBy) SELECTED else 2); if (k == "ai" && !aiOk) alpha = 0.35f },
                        Ink.lp(0, weight = 1f).apply { if (i > 0) leftMargin = Ink.dp(this@CardActivity, 6) })
                }
                p.addView(seg, Ink.lp().apply { bottomMargin = Ink.dp(this@CardActivity, 8) })
            }
            val out = Ink.text(this@CardActivity, "", size); p.addView(out)
            if (translateBy == "ai" && aiOk) {
                val d = answer(Ask.QUICK)
                val dots = if (d.isCompleted) null else launch { var n = 0; while (true) { out.text = getString(R.string.thinking) + ".".repeat(n % 3 + 1); n++; delay(700) } }
                val r = d.await(); dots?.cancel()
                r.fold({ answered(out, render(TRANSLATE, it)) }, { out.text = friendly(it) })
            } else offline(p, out)
        }
    }

    private suspend fun offline(p: LinearLayout, out: TextView) {
        val root = prefs.modelsDir; val from = prefs.bookLang; val to = prefs.answerCode
        if (from.isBlank() || to.isBlank() || !Engine.ready(root, from, to)) {
            out.text = getString(R.string.mt_missing)
            if (kind != Kind.WORD) glossInto(p.also { it.addView(View(this), Ink.lp(h = Ink.dp(this, 12))) }) { w ->
                if (pages[DICT] == null) pages[DICT] = Ink.vertical(this); open(DICT); pages[DICT]?.let { d -> fillArticles(d, w) { fillGloss(d) } } }
            return
        }
        val key = "offline-$from"
        val saved = withContext(Dispatchers.IO) { runCatching { store.answer(query, to, key, 1) }.getOrNull() }
        if (saved == null) out.text = getString(R.string.mt_working)
        val r = saved?.let { Result.success(it) } ?: withContext(Dispatchers.IO) { runCatching {
            Engine.translate(root, from, to, query, keep = roomy()).also { runCatching { store.putAnswer(query, to, key, 1, "bergamot", it) } }
        } }
        r.fold({ out.text = it; if (Engine.steps(from, to).size > 1) note(p, getString(R.string.mt_via_english)) }, { out.text = it.message ?: it.javaClass.simpleName })
    }

    /** Keep both models loaded only with plenty of free memory (≈ 300 MB each). */
    private fun roomy(): Boolean = android.app.ActivityManager.MemoryInfo().also {
        (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(it) }.availMem > 1_500_000_000L

    /** An AI tab: Analysis and Context read the detailed answer. */
    private fun fillAi(p: LinearLayout, tab: String) {
        val out = Ink.text(this, "", size); p.addView(out)
        val ask = if (tab == "translate") Ask.QUICK else Ask.DETAIL
        launch {
            val d = answer(ask)
            val dots = if (d.isCompleted) null else launch { var n = 0; while (true) { out.text = getString(R.string.thinking) + ".".repeat(n % 3 + 1); n++; delay(700) } }
            val r = d.await()
            dots?.cancel()
            r.fold({ answered(out, render(tab, it)) }, { failed(p, out, tab, it.takeUnless { e -> e is Offline }) })
        }
    }

    private fun answered(out: TextView, md: String) {
        out.text = android.text.Html.fromHtml(Ink.markdown(md), android.text.Html.FROM_HTML_MODE_COMPACT)
    }

    private fun starred(on: Boolean) {
        starButton.setImageResource(if (on) R.drawable.ic_star_on else R.drawable.ic_star)
        (starButton.parent as View).apply { contentDescription = getString(if (on) R.string.unsave_word else R.string.save_word); tooltipText = contentDescription }
    }

    /** ☆ saves the selection with one short meaning: the cached translation, else the dictionaries; ★ removes it. */
    private fun toggleSaved() = launch {
        val lang = prefs.answerLang; val script = answerScript
        val on = withContext(Dispatchers.IO) { runCatching {
            if (store.isSaved(query)) { store.unsave(query); false } else {
                val ai = store.answer(query, lang, Ask.QUICK.section, Ask.QUICK.version)
                val dict = if (ai != null || kind != Kind.WORD) null else
                    prefs.library().gloss(query, prefs.order, prefs.disabled, script).firstOrNull()?.let { g ->
                        (if (g.base.equals(g.word, true)) "" else "→ ${g.base} · ") + g.meaning }
                store.save(query, kindName, if (ai != null) Store.brief(ai) else dict.orEmpty()); true
            }
        }.getOrNull() } ?: return@launch
        starred(on)
    }

    /** The error, and for a phrase or passage the offline gloss under it; the tab asks again next time. */
    private fun failed(p: LinearLayout, out: TextView, tab: String, e: Throwable?) {
        out.text = if (e == null) getString(R.string.err_offline) else friendly(e)
        pages.remove(tab)
        // A tap on a word opens its articles in the Dictionary tab (an empty page there skips the gloss fill).
        if (kind != Kind.WORD) glossInto(p.also { it.addView(View(this), Ink.lp(h = Ink.dp(this, 12))) }) { w ->
            if (pages[DICT] == null) pages[DICT] = Ink.vertical(this)
            open(DICT); pages[DICT]?.let { d -> fillArticles(d, w) { fillGloss(d) } }
        }
    }

    private var savedKind = "word"

    /** My words in the card: saved words or phrases; tap opens one in the card, ✕ removes it. */
    private fun fillSaved(p: LinearLayout) {
        p.removeAllViews()
        launch {
            val lang = prefs.answerLang; val kindNow = savedKind
            val (items, counts) = withContext(Dispatchers.IO) { runCatching {
                store.saved(kindNow, lang) to listOf(store.count("saved", "word"), store.count("saved", "phrase"))
            }.getOrNull() } ?: return@launch
            val seg = Ink.horizontal(this@CardActivity)
            listOf("word" to R.string.list_words, "phrase" to R.string.list_phrases).forEachIndexed { i, (k, label) ->
                seg.addView(Ink.button(this@CardActivity, getString(label) + " (" + counts[i] + ")", size * 0.75f) { savedKind = k; fillSaved(p) }
                    .apply { background = Ink.border(this@CardActivity, if (k == kindNow) SELECTED else 2) },
                    Ink.lp(0, weight = 1f).apply { if (i > 0) leftMargin = Ink.dp(this@CardActivity, 6) })
            }
            p.addView(seg, Ink.lp().apply { bottomMargin = Ink.dp(this@CardActivity, 8) })
            if (items.isEmpty()) note(p, getString(R.string.list_empty_saved))
            items.forEach { e ->
                val r = Ink.horizontal(this@CardActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                val meaning = if (e.meaning.isBlank()) "" else " — " + Ink.escape(e.meaning)
                r.addView(Ink.text(this@CardActivity, "<b>${Ink.escape(e.text)}</b>$meaning", size * 0.85f, html = true).apply {
                    val q = Ink.dp(this@CardActivity, 5); setPadding(0, q, q, q)
                    setOnClickListener { startActivity(Intent(this@CardActivity, CardActivity::class.java)
                        .setAction("colordict.intent.action.SEARCH").putExtra("EXTRA_QUERY", e.text)) }
                }, Ink.lp(0, weight = 1f))
                r.addView(Ink.button(this@CardActivity, "✕", size * 0.75f) {
                    launch {
                        withContext(Dispatchers.IO) { runCatching { store.unsave(e.text) } }
                        if (e.text == query) starred(false)
                        fillSaved(p)
                    }
                })
                p.addView(r)
            }
        }
    }

    companion object {
        const val DICT = "dict"; const val SAVED = "saved"; const val TRANSLATE = "translate"
        /** Border of the open tab: a little heavier than the others, not bold. */
        const val SELECTED = 4
    }
}
