package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kafeg.inkpeek.Net
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.Store
import io.github.kafeg.inkpeek.ai.Action
import io.github.kafeg.inkpeek.ai.Ai
import io.github.kafeg.inkpeek.ai.Answers
import io.github.kafeg.inkpeek.ai.Ask
import io.github.kafeg.inkpeek.ai.Kind
import io.github.kafeg.inkpeek.mt.Engine
import io.github.kafeg.inkpeek.mt.OfflineTranslation
import io.github.kafeg.inkpeek.stardict.cleanPhrase
import io.github.kafeg.inkpeek.stardict.answer
import io.github.kafeg.inkpeek.stardict.book
import io.github.kafeg.inkpeek.stardict.cleanQuery
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The pop-up card: the header (the word's first line from the packs), the open tab's page, the icon bar. The tabs fill
 * themselves: [DictionaryPage], [TranslatePage], [AiPage] (Analysis, Context), [WordsPage].
 */
class CardActivity : Activity(), CoroutineScope by MainScope() {
    lateinit var prefs: Prefs; private set
    val store by lazy { Store.get(this) }
    var query = ""; private set
    /** The words looked up before this one in this card ("Look up" from a text, an entry of Notes); ← returns to them. */
    private var before: List<String> = emptyList()
    /** Where the card was on each of them: "tab|place in the dictionary|scroll" (see [at]). */
    private var beforeAt: List<String> = emptyList()
    /** The scroll to come back to once the restored page has its content. */
    var restoreScroll = -1; private set
    val kind get() = Kind.of(query)
    /** This look counted in the history: the count under the header waits for it. */
    private var seenJob: kotlinx.coroutines.Job? = null
    private val kindName get() = if (kind == Kind.WORD) "word" else "phrase"
    /** Body text size (sp); the header is larger. */
    val size: Float get() = prefs.cardText.toFloat()
    val online: Boolean get() = Net.online(this)
    val answers by lazy { Answers(prefs, store, query) { online } }
    val mt by lazy { OfflineTranslation(prefs, store, query, ::roomy) { io.github.kafeg.inkpeek.mt.Device.of(this) } }
    val dictionary = DictionaryPage(this)
    val ai = AiPage(this)
    private val translate = TranslatePage(this)
    private val words = WordsPage(this)

    private lateinit var scroll: ScrollView
    private lateinit var root: LinearLayout
    private var width = 0
    private var maxHeight = 0
    private val pages = HashMap<String, LinearLayout>()
    private val tabButtons = LinkedHashMap<String, View>()
    private var shown = ""
    fun showing(tab: String) = shown == tab
    private lateinit var titleView: TextView
    private lateinit var subView: TextView
    private lateinit var titleBox: LinearLayout
    /** "←" in the icon bar: shown while a word's article is open from the list or the gloss. */
    private lateinit var backButton: View
    private lateinit var starButton: ImageView
    /** The AI tabs: a module with a part ticked (AI → Modules). */
    private val actions get() = if (prefs.aiOn) Action.ALL.filter { ai.ask(it.id).active.isNotEmpty() } else emptyList()
    /** The AI requests this card can show: Translate by AI, and the AI tabs that are not hidden. */
    val shownAsks: List<Ask> get() = if (!prefs.aiOn) emptyList() else (if (prefs.aiTranslate) listOf(Ask.QUICK) else emptyList()) + actions.map { ai.ask(it.id) }

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Ink.localized(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        Ink.theme(this, card = true)
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        // A word loses its edge punctuation; a phrase keeps "?" or "!" — the sentence type is part of the meaning.
        val raw = textOf(intent).orEmpty().take(4000) // about a page
        query = if (Kind.of(cleanQuery(raw)) == Kind.WORD) cleanQuery(raw) else cleanPhrase(raw)
        if (query.isEmpty()) { finish(); return }
        before = intent.getStringArrayListExtra(BEFORE).orEmpty()
        beforeAt = intent.getStringArrayListExtra(BEFORE_AT).orEmpty().let { if (it.size == before.size) it else before.map { "" } }
        val back = intent.getStringExtra(AT)?.split('|')
        // One look per opening: not again when the card is made anew (theme, language) or returned to with ←.
        if (back == null && !intent.getBooleanExtra(COUNTED, false)) { intent.putExtra(COUNTED, true)
            seenJob = launch(Dispatchers.IO) { runCatching { store.seen(query, kindName) } } }
        build()
        placeWindow()
        if (kind == Kind.WORD) head()
        // A phrase: word by word and the offline translation start at once; the translation fills Translate and the history
        // line. Not when offline translation is off, nor with an AI model (heavy): that one starts when its tab opens.
        else {
            dictionary.warm()
            if (offlineOk && prefs.mtMethod != "llm")
                launch { mt.start(this@CardActivity).await().text?.getOrNull()?.let { t -> withContext(Dispatchers.IO) { runCatching { store.meaning(query, kindName, t, prefs.answerLang) } } } }
        }
        // Back from a word looked up here: the tab, article and scroll it was left at; else where a new card starts.
        if (back != null && back.size == 3 && back[0] in tabButtons) {
            dictionary.restore = back[1]; restoreScroll = back[2].toIntOrNull() ?: -1
            open(back[0])
            if (back[0] != DICT && restoreScroll >= 0) scroll.postDelayed({ scrollBack() }, 400)
        } else if (kind == Kind.WORD) open(DICT) else launch { open(withContext(Dispatchers.IO) { phraseTab() }) }
    }

    /**
     * A phrase's first tab (Card → Opening): the offline translation, by AI (a setting chosen, so AI asks at once), word
     * by word, or an AI tab; what cannot be shown now (AI off, no key, no network) gives the offline translation, else
     * Dictionary (word by word). Off the main thread ([Answers.block] reads the saved answers).
     */
    private fun phraseTab(): String {
        fun offline() = if (offlineOk) TRANSLATE.also { translate.by = "offline"; translate.auto = true } else DICT
        return when (val o = prefs.phraseOpen) {
            "offline" -> offline()
            "ai" -> if (aiTranslate && answers.block(Ask.QUICK) == null) TRANSLATE.also { translate.by = "ai" } else offline()
            "analysis", "context" -> if (actions.any { it.id == o } && answers.block(ai.ask(o)) == null) o else offline()
            else -> DICT
        }
    }

    /** The scroll a returned-to page was left at, once. */
    fun scrollBack() { if (restoreScroll >= 0) { val y = restoreScroll; restoreScroll = -1; scroll.post { scroll.scrollTo(0, y) } } }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); recreate() }
    override fun onPause() { TextActions.end(); super.onPause() }
    override fun onDestroy() { cancel(); super.onDestroy() }
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        // Off the main thread: a release waits for a running translation.
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) Thread { Engine.release(); io.github.kafeg.inkpeek.mt.Llm.release() }.start()
    }

    private fun textOf(i: Intent): String? = when (i.action) {
        "colordict.intent.action.SEARCH" -> i.getStringExtra("EXTRA_QUERY")
        Intent.ACTION_PROCESS_TEXT -> i.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString()
        Intent.ACTION_SEND -> i.getStringExtra(Intent.EXTRA_TEXT)
        else -> i.getStringExtra("EXTRA_QUERY") ?: i.getStringExtra(Intent.EXTRA_TEXT)
    }

    /** The card (header, body) with the icon bar at its bottom under a thin line: tabs · save, Notes · close. */
    private fun build() {
        val pad = Ink.dp(this, 14)
        val card = Ink.vertical(this).apply { background = Ink.border(this@CardActivity, 3, 10); setPadding(pad, pad, pad, pad) }
        root = card
        val head = FrameLayout(this)
        val long = query.length > 40 // long selections: smaller, 2 lines; the whole text still goes to the lookup and AI
        titleView = Ink.text(this, query, if (long) size * 0.95f else size * 1.3f, bold = !long).apply {
            // No "…": Android's selection goes wrong on ellipsized text (a double tap took it all, a triple missed a word).
            maxLines = if (long) 2 else 3; setLineSpacing(0f, 1f)
            TextActions.selectable(this, paragraph = false)
        }
        // Under the title: the selected form, the same word in another book language.
        subView = Ink.text(this, "", size * 0.72f).apply { visibility = View.GONE; TextActions.selectable(this) }
        titleBox = Ink.vertical(this).apply { addView(titleView); addView(subView) }
        head.addView(titleBox, FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(head)
        scroll = ScrollView(this).apply { overScrollMode = android.view.View.OVER_SCROLL_NEVER }
        card.addView(scroll, Ink.lp(h = 0, weight = 1f).apply { topMargin = Ink.dp(this@CardActivity, 8) })
        scroll.viewTreeObserver.addOnGlobalLayoutListener { fit() }
        // Small print over the icon bar: AI off or no network, or what the open tab says about itself.
        baseHint = if (prefs.aiOn && !online) getString(R.string.offline) else null
        hintView = Ink.text(this, "", size * 0.65f); showHint()
        card.addView(hintView, Ink.lp().apply { topMargin = Ink.dp(this@CardActivity, 6) })

        val tabs = listOfNotNull(Triple(DICT, R.drawable.ic_dictionary, getString(R.string.tab_dictionary)),
            Triple(TRANSLATE, R.drawable.ic_translate, getString(R.string.tab_translate)).takeIf { canTranslate }) + actions.map { a ->
            Triple(a.id, if (a.id == "analysis") R.drawable.ic_analysis else R.drawable.ic_context, Ink.label(this, a))
        }
        val n = tabs.size + if (prefs.cardSave) 4 else 2; val gap = Ink.dp(this, 5) // + save, Notes, back, close
        val side = minOf(Ink.dp(this, 44), (resources.displayMetrics.widthPixels * prefs.cardWidth / 100 - 2 * pad - gap * (n + 3)) / n)
        val bar = Ink.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun add(v: View, first: Boolean = false) = bar.addView(v, Ink.lp(android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (!first) leftMargin = gap })
        tabs.forEachIndexed { i, (id, icon, label) ->
            val isAi = id != DICT && id != TRANSLATE
            add(iconButton(icon, label, side, ai = isAi) { if (isAi) launch { val r = withContext(Dispatchers.IO) { answers.block(ai.ask(id)) }
                if (r != null) toast(getString(r)) else open(id) } else open(id) }.also { tabButtons[id] = it }, i == 0)
        }
        // AI tabs are dimmed while AI cannot answer; a saved answer still opens offline.
        launch { val blocked = withContext(Dispatchers.IO) { actions.associate { it.id to answers.block(ai.ask(it.id)) } }
            actions.forEach { tabButtons[it.id]?.alpha = if (blocked[it.id] == null) 1f else Ink.OFF } }
        val star = iconButton(R.drawable.ic_star, getString(R.string.save_word), side) { toggleSaved() }
        starButton = star.findViewWithTag("icon")
        if (prefs.cardSave) {
            bar.addView(View(this), Ink.lp(gap * 2, 1)); add(star)
            add(iconButton(R.drawable.ic_words, getString(R.string.tab_words), side) { open(SAVED) }.also { tabButtons[SAVED] = it })
        }
        bar.addView(View(this), Ink.lp(0, 1, weight = 1f))
        // "←" in the free space before Close: from an article back to the list or the gloss.
        backButton = iconButton(R.drawable.ic_back, getString(R.string.back), side) { back() }.apply { visibility = View.GONE }
        add(backButton)
        add(iconButton(R.drawable.ic_close, getString(R.string.close), side) { finish() })
        launch { if (withContext(Dispatchers.IO) { runCatching { isSaved() }.getOrDefault(false) }) starred(true) }

        // No icon bar (Card → Opening): the card is the open page only; Back or a tap outside closes it.
        if (prefs.cardBar) {
            card.addView(View(this).apply { setBackgroundColor(Ink.fg(this@CardActivity)) },
                Ink.lp(h = Ink.dp(this, 1)).apply { topMargin = Ink.dp(this@CardActivity, 8); bottomMargin = Ink.dp(this@CardActivity, 8) })
            card.addView(bar)
        }
        setContentView(card)
    }

    /** A bordered icon; long press shows its name; an AI tab has a small "AI" in its corner. */
    private fun iconButton(icon: Int, label: String, side: Int, ai: Boolean = false, click: () -> Unit): View {
        val q = side / 5
        // An AI icon is drawn a bit smaller: its top-right corner holds the "AI" label.
        val img = ImageView(this).apply { setImageResource(icon); tag = "icon"; setColorFilter(Ink.fg(this@CardActivity))
            if (ai) setPadding(q, q + q / 2, q + q / 2, q) else setPadding(q, q, q, q) }
        val name = if (ai) "$label · AI" else label
        return Ink.vertical(this).apply {
            gravity = Gravity.CENTER_HORIZONTAL; background = Ink.border(this@CardActivity)
            contentDescription = name; tooltipText = name; isClickable = true; setOnClickListener { click() }
            addView(if (!ai) img else FrameLayout(this@CardActivity).apply {
                addView(img, FrameLayout.LayoutParams(side, side))
                // Clear of the border even when thick (the open tab): 6 dp from the box corner.
                val m = Ink.dp(this@CardActivity, 6)
                addView(Ink.text(this@CardActivity, "AI", 9f, bold = true).apply { includeFontPadding = false; typeface = android.graphics.Typeface.DEFAULT_BOLD },
                    FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.WRAP_CONTENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END)
                        .apply { topMargin = m; rightMargin = m })
            }, LinearLayout.LayoutParams(side, side))
        }
    }

    /**
     * Size and place from Card → Appearance. "Where the reader asks": ColorDict's EXTRA_GRAVITY (top or bottom, away from the
     * selected word) and EXTRA_HEIGHT (at most that tall), sent by Librera and FBReader.
     */
    private fun placeWindow() {
        val dm = resources.displayMetrics
        val reader = prefs.cardPlace == "reader"
        val askedHeight = if (reader) intent.getIntExtra("EXTRA_HEIGHT", 0) else 0
        width = dm.widthPixels * prefs.cardWidth / 100
        maxHeight = dm.heightPixels * (if (prefs.cardHeight == 0) 80 else prefs.cardHeight) / 100
        if (askedHeight > 0) maxHeight = minOf(maxHeight, askedHeight)
        window.setLayout(width, if (prefs.cardHeight == 0) maxHeight / 2 else maxHeight)
        val vertical = when (prefs.cardPlace) {
            "top" -> Gravity.TOP; "bottom" -> Gravity.BOTTOM
            "reader" -> (intent.getIntExtra("EXTRA_GRAVITY", Gravity.CENTER_VERTICAL) and Gravity.VERTICAL_GRAVITY_MASK).takeIf { it != 0 } ?: Gravity.CENTER_VERTICAL
            else -> Gravity.CENTER_VERTICAL
        }
        window.setGravity(vertical or Gravity.CENTER_HORIZONTAL)
        if (vertical != Gravity.CENTER_VERTICAL) window.attributes = window.attributes.apply { y = Ink.dp(this@CardActivity, 8) }
        if (prefs.cardDim > 0) { window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND); window.setDimAmount(prefs.cardDim / 100f) }
        else window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        window.setWindowAnimations(0)
    }

    /** Height by content: the window follows the open page, between a small card and [maxHeight]. */
    private fun fit() {
        if (prefs.cardHeight != 0 || !::root.isInitialized) return
        val page = scroll.getChildAt(0) ?: return
        val target = (root.height - scroll.height + page.height).coerceIn(minOf(Ink.dp(this, 140), maxHeight), maxHeight)
        if (kotlin.math.abs(window.attributes.height - target) > Ink.dp(this, 6)) window.setLayout(width, target)
    }

    /** A tab's page, filled the first time unless [fill] is false (the Dictionary tab opened on a word from elsewhere). */
    fun page(tab: String, fill: Boolean = true): LinearLayout = pages[tab] ?: Ink.vertical(this).also { p ->
        pages[tab] = p
        if (fill) when (tab) { DICT -> dictionary.fill(p); SAVED -> words.fill(p); TRANSLATE -> translate.fill(p); else -> ai.fill(p, tab) }
    }

    private lateinit var hintView: TextView
    private var baseHint: String? = null

    private val hints = HashMap<String, String?>()

    /** [tab]'s small line at the bottom: [s], or the card's own note when null; shown while the tab is open. */
    fun hint(tab: String, s: String?) { hints[tab] = s; showHint() }
    private fun showHint() { val t = hints[shown] ?: baseHint; hintView.text = t.orEmpty(); hintView.visibility = if (t.isNullOrBlank()) View.GONE else View.VISIBLE }

    /** Shows the tab, filling it the first time. */
    fun open(tab: String) {
        tabButtons.forEach { (t, b) -> b.background = Ink.border(this, if (t == tab) SELECTED else 2) }
        val p = page(tab)
        scroll.removeAllViews(); scroll.addView(p); scroll.scrollTo(0, 0)
        shown = tab; showBack(); showHint()
    }

    val offlineOk: Boolean by lazy { prefs.offlineReady() }
    /** AI translation can be asked: AI on and its module on. */
    val aiTranslate: Boolean get() = prefs.aiOn && prefs.aiTranslate
    /** Translate is there when AI translation or offline translation can work; else no icon at all. */
    val canTranslate: Boolean get() = aiTranslate || offlineOk

    /** The tab is filled again on its next opening (its AI request failed). */
    fun forget(tab: String) { pages.remove(tab) }

    fun scrollTop() = scroll.scrollTo(0, 0)

    fun showBack() { backButton.visibility = if (shown == DICT && dictionary.back != null || before.isNotEmpty()) View.VISIBLE else View.GONE }

    /** [text] in this card, this word kept to come back to. */
    fun lookUp(text: String) = startActivity(WordsList.card(this, text).putStringArrayListExtra(BEFORE, ArrayList(before + query))
        .putStringArrayListExtra(BEFORE_AT, ArrayList(beforeAt + "$shown|${dictionary.place}|${scroll.scrollY}")))

    /** ←: from an article to its list, else to the word looked up before; false when there is nothing to go back to. */
    private fun back(): Boolean {
        if (shown == DICT && dictionary.back != null) { dictionary.back?.invoke(); return true }
        val prev = before.lastOrNull() ?: return false
        startActivity(WordsList.card(this, prev).putStringArrayListExtra(BEFORE, ArrayList(before.dropLast(1)))
            .putStringArrayListExtra(BEFORE_AT, ArrayList(beforeAt.dropLast(1))).putExtra(AT, beforeAt.lastOrNull().orEmpty())); return true
    }

    /** Where a line comes from: a pack, "InkPeek · Немецкий → Русский", or another dictionary's name. */
    fun source(d: io.github.kafeg.inkpeek.stardict.StarDict): String = DictionaryPage.source(this, prefs.library(), d)

    /** System Back from a word's article returns to the list or the gloss first. */
    @Deprecated("Activity API") override fun onBackPressed() {
        if (!back()) @Suppress("DEPRECATION") super.onBackPressed()
    }

    fun note(p: LinearLayout, s: String) = p.addView(Ink.text(this, s, size * 0.85f))

    /** An AI answer: its "→" drawn in the middle of the line, as everywhere in the card. */
    fun markdown(out: TextView, md: String) { out.text = Ink.arrows(this, android.text.Html.fromHtml(Ink.markdown(md), android.text.Html.FROM_HTML_MODE_COMPACT), out.textSize) }

    fun toast(s: String) = Ink.toast(this, s)

    /** The word's header from the packs, offline: "das Haus (Hauses, мн. Häuser)"; its first line goes to the history. */
    private fun head() = launch {
        if (!prefs.packGrammar) return@launch
        val heads = withContext(Dispatchers.IO) { runCatching { prefs.library().heads(query, prefs.answerCode, prefs.packDictionaries()) }.getOrDefault(emptyList()) }
        val first = heads.firstOrNull() ?: return@launch
        launch(Dispatchers.IO) { runCatching { store.meaning(query, kindName, first.line, prefs.answerLang) } }
        titleView.text = first.header
        // How common the word is and how often it was looked up here, in any of its forms.
        seenJob?.join()
        val (rank, looks) = withContext(Dispatchers.IO) { runCatching {
            prefs.library().rank(first.word, first.book) to store.run { base(query, kindName, first.word); looks(first.word) }
        }.getOrDefault(0 to 0) }
        val facts = listOfNotNull(Facts.frequency(this@CardActivity, rank), Facts.looks(this@CardActivity, looks)).joinToString(" · ")
        // The selected form, and the same word in another book language ("Gift" is German and English).
        val sub = listOfNotNull(if (first.word.equals(query, true)) null else getString(R.string.form_of_sel, query), heads.getOrNull(1)?.header,
            facts.ifBlank { null }).joinToString("\n")
        subView.text = sub; subView.visibility = if (sub.isBlank()) View.GONE else View.VISIBLE
    }

    fun starred(on: Boolean) {
        starButton.setImageResource(if (on) R.drawable.ic_star_on else R.drawable.ic_star)
        (starButton.parent as View).apply { contentDescription = getString(if (on) R.string.unsave_word else R.string.save_word); tooltipText = contentDescription }
    }

    /**
     * What ☆ saves: a word as its base form from the packs ("andere" → "ander"), also after its article ("das Haus" →
     * "Haus": the pack's header starts so); else the selection as it is. Blocking. (text, kind)
     */
    private fun saveKey(): Pair<String, String> {
        val lib = prefs.library(); val packs = prefs.packDictionaries()
        if (kind == Kind.WORD) return (lib.heads(query, prefs.answerCode, packs).firstOrNull()?.word ?: query) to "word"
        val w = query.trim().split(Regex("\\s+"))
        if (w.size == 2) lib.heads(w[1], prefs.answerCode, packs).firstOrNull()?.takeIf { it.header.startsWith(query.trim(), ignoreCase = true) }
            ?.let { return it.word to "word" }
        return query to kindName
    }

    /** Saved as its base form, or as the selection itself (by an older version, or while the packs were indexed). */
    fun isSaved() = store.isSaved(saveKey().first) || store.isSaved(query)

    /** ☆ saves the selection (a word as its base form) with one short line: the saved AI translation, else the dictionary's first line or the offline translation; ★ removes it. */
    private fun toggleSaved() = launch {
        val on = withContext(Dispatchers.IO) { runCatching {
            val (key, keyKind) = saveKey()
            if (isSaved()) { store.unsave(key); store.unsave(query); false } else {
                val line = answers.saved() ?: if (keyKind != "word") mt.done() else prefs.library().heads(key, prefs.answerCode, prefs.packDictionaries())
                    .firstOrNull()?.let { if (prefs.packGrammar) it.line else "${it.word} — ${it.meaning}" }
                store.save(key, keyKind, line.orEmpty(), prefs.answerLang, from = query); true
            }
        }.getOrNull() } ?: return@launch
        starred(on)
    }

    /** Keep both models loaded only with plenty of free memory (≈ 300 MB each). */
    private fun roomy(): Boolean = android.app.ActivityManager.MemoryInfo().also {
        (getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager).getMemoryInfo(it) }.availMem > 1_500_000_000L

    companion object {
        private const val BEFORE = "inkpeek.before"; private const val BEFORE_AT = "inkpeek.before.at"; private const val AT = "inkpeek.at"
        /** The intent's look is counted (the card made again keeps its intent). */
        private const val COUNTED = "inkpeek.counted"
        const val DICT = "dict"; const val SAVED = "saved"; const val TRANSLATE = "translate"
        /** Border of the open tab: a little heavier than the others, not bold. */
        const val SELECTED = 4
    }

    /** A tap beside a selection ends it and its menu. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean { TextActions.outside(ev); swipe.onTouchEvent(ev); return super.dispatchTouchEvent(ev) }

    /** Full articles: a swipe left or right turns to the next or previous dictionary. */
    private val swipe by lazy { android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
            val turn = dictionary.turn ?: return false
            if (shown != DICT || kotlin.math.abs(vx) < 2 * kotlin.math.abs(vy) || kotlin.math.abs(vx) < 500) return false
            turn(if (vx < 0) 1 else -1); return true
        }
    }) }
}
