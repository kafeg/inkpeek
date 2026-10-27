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

    /** The open page's frame: a ScrollView, or by pages a plain frame moved by [android.view.View.scrollTo]. */
    private lateinit var scroll: FrameLayout
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
    /** The AI tab's sub-tabs: the modules on ("analysis", "context"); none — no AI tab. */
    val aiModules: List<String> get() = actions.map { it.id }
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
        if (prefs.history && back == null && !intent.getBooleanExtra(COUNTED, false)) { intent.putExtra(COUNTED, true)
            seenJob = launch(Dispatchers.IO) { runCatching { store.seen(query, kindName) } } }
        build()
        placeWindow()
        if (kind == Kind.WORD) head()
        // A phrase: word by word and the offline translation start at once; the translation fills Translate and the history
        // line. Not when offline translation is off, nor with an AI model (heavy): that one starts when its tab opens.
        else {
            dictionary.warm()
            if (offlineOk && prefs.mtMethod != "llm")
                launch { mt.start(this@CardActivity).await().text?.getOrNull()?.takeIf { prefs.history }?.let { t -> withContext(Dispatchers.IO) { runCatching { store.meaning(query, kindName, t, prefs.answerLang) } } } }
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
    private fun phraseTab(): String =
        when (val o = Opening.pick(prefs.phraseOpen, Opening.choices(offlineOk, aiTranslate, aiModules)) { k ->
            answers.block(if (k == "ai") Ask.QUICK else ai.ask(k)) != null }) {
            "offline" -> TRANSLATE.also { translate.by = "offline"; translate.auto = true }
            "ai" -> TRANSLATE.also { translate.by = "ai" }
            "analysis", "context" -> AI.also { ai.by = o }
            else -> DICT
        }

    /**
     * A title longer than its lines is not selected or scrolled inside: its lines end in "…" and a tap shows the whole
     * text in a window, selectable there (a selection inside a cut text scrolls it and takes it all).
     */
    private fun fitTitle() {
        closeTitle()
        titleView.apply { ellipsize = null; setOnClickListener(null); TextActions.selectable(this, paragraph = false) }
        titleView.post { cutTitle() }
    }

    private fun cutTitle() {
        val l = titleView.layout ?: return
        if (l.lineCount <= titleView.maxLines) return
        val full = titleView.text
        titleView.apply { setTextIsSelectable(false); setOnTouchListener(null); ellipsize = android.text.TextUtils.TruncateAt.END; text = full
            setOnClickListener { showWholeTitle(full) } }
    }

    private var titleSp = 0f; private var titleBold = false
    private var titleOver: View? = null

    /**
     * The whole title in its place, the same font and margins, as if it just grew: the page under it moves down (a very
     * long text scrolls within half the card). ▲ under it or Back closes it.
     */
    private fun showWholeTitle(full: CharSequence) {
        if (titleOver != null) return
        val tv = Ink.text(this, full, titleSp, bold = titleBold).apply { setLineSpacing(0f, 1f); TextActions.selectable(this, paragraph = false) }
        // Under the text: a line's room, ▲ in the middle (closes it), then a line — apart from the page under it.
        val up = ImageView(this).apply { setImageResource(R.drawable.ic_collapse); setColorFilter(Ink.fg(this@CardActivity)); contentDescription = getString(R.string.close)
            isClickable = true; setOnClickListener { closeTitle() } }
        val box = Ink.vertical(this).apply { setBackgroundColor(Ink.bg(this@CardActivity)); addView(tv)
            addView(up, LinearLayout.LayoutParams(Ink.dp(this@CardActivity, 48), Ink.dp(this@CardActivity, 32)).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = (titleSp * resources.displayMetrics.scaledDensity * 0.6f).toInt() })
            addView(View(this@CardActivity).apply { setBackgroundColor(Ink.fg(this@CardActivity)) }, Ink.lp(h = Ink.dp(this@CardActivity, 2)).apply { topMargin = Ink.dp(this@CardActivity, 4) })
            setPadding(0, 0, 0, Ink.dp(this@CardActivity, 6)) }
        // In the title's place: the page under it moves down; a very long text scrolls within half the card.
        val over = ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER; addView(box) }
        titleView.visibility = View.GONE
        titleBox.addView(over, 0, Ink.lp())
        over.post { val cap = root.height / 2; if (over.height > cap) over.layoutParams = over.layoutParams.apply { height = cap } }
        titleOver = over
    }

    private fun closeTitle(): Boolean = titleOver?.let { titleBox.removeView(it); titleView.visibility = View.VISIBLE; titleOver = null; true } ?: false

    /** The scroll a returned-to page was left at, once. */
    fun scrollBack() { if (restoreScroll >= 0) { val y = restoreScroll; restoreScroll = -1; scroll.post { if (prefs.cardPaged) pageAt(y) else scroll.scrollTo(0, y) } } }

    /** By pages: the page holding [y], turned to from the top, so ◀ pages and the count are right. */
    private fun pageAt(y: Int) {
        val max = ((scroll.getChildAt(0)?.height ?: 0) - scroll.height).coerceAtLeast(0)
        pagesBefore.clear(); var at = 0
        while (at < max) { val next = nextPage(at, max); if (next > y || next <= at) break; pagesBefore += at; at = next }
        scroll.scrollTo(0, at)
    }

    override fun onNewIntent(intent: Intent) { super.onNewIntent(intent); setIntent(intent); recreate() }
    override fun onPause() { TextActions.end(); super.onPause() }
    override fun onDestroy() {
        cancel()
        if (isFinishing && !prefs.history) Thread { runCatching { store.dropUnkept() } }.start()
        super.onDestroy()
    }
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
        titleSp = if (long) size * 0.95f else size * 1.3f; titleBold = !long
        titleView = Ink.text(this, query, titleSp, bold = titleBold).apply {
            // No "…": Android's selection goes wrong on ellipsized text (a double tap took it all, a triple missed a word).
            maxLines = if (long) 2 else 3; setLineSpacing(0f, 1f)
            TextActions.selectable(this, paragraph = false)
        }
        fitTitle()
        // Under the title: the selected form, how common the word is and how often it was looked up.
        subView = Ink.text(this, "", size * 0.72f).apply { visibility = View.GONE; TextActions.selectable(this) }
        titleBox = Ink.vertical(this).apply { addView(titleView); addView(subView) }
        head.addView(titleBox, FrameLayout.LayoutParams(android.view.ViewGroup.LayoutParams.MATCH_PARENT, android.view.ViewGroup.LayoutParams.WRAP_CONTENT))
        card.addView(head)
        // By pages (Card → Appearance): no ScrollView (some readers' firmware takes any swipe over one for a scroll and
        // redraws the whole screen after it); a frame as tall as the card, its page as tall as it needs, a swipe turns it (see [swipe]).
        scroll = if (!prefs.cardPaged) ScrollView(this).apply { overScrollMode = View.OVER_SCROLL_NEVER } else object : FrameLayout(this) {
            override fun onMeasure(w: Int, h: Int) {
                super.onMeasure(w, h)
                getChildAt(0)?.measure(MeasureSpec.makeMeasureSpec(measuredWidth, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            }
            override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
                val child = getChildAt(0) ?: return
                child.layout(0, 0, child.measuredWidth, child.measuredHeight)
                // New content shorter than where the old one was turned to: back within it, the pages past it forgotten.
                val max = (child.measuredHeight - height).coerceAtLeast(0)
                if (scrollY > max) scrollTo(0, max)
                pagesBefore.removeAll { it > max }
            }
        }.apply { clipChildren = true }
        card.addView(scroll, Ink.lp(h = 0, weight = 1f).apply { topMargin = Ink.dp(this@CardActivity, 8) })
        scroll.viewTreeObserver.addOnGlobalLayoutListener { fit() }
        // Small print over the icon bar: AI off or no network, or what the open tab says about itself.
        baseHint = if (prefs.aiOn && !online) getString(R.string.offline) else null
        hintView = Ink.text(this, "", size * 0.65f); showHint()
        card.addView(hintView, Ink.lp().apply { topMargin = Ink.dp(this@CardActivity, 6) })
        if (prefs.cardPaged) { scroll.setOnScrollChangeListener { _, _, _, _, _ -> showPage() }; scroll.viewTreeObserver.addOnGlobalLayoutListener { showPage() } }

        val tabs = listOfNotNull(Triple(DICT, R.drawable.ic_dictionary, getString(R.string.tab_dictionary)),
            Triple(TRANSLATE, R.drawable.ic_translate, getString(R.string.tab_translate)).takeIf { canTranslate },
            // One AI tab, its modules inside (as Translate holds Offline and AI); named by the module when it is one.
            actions.singleOrNull()?.let { a -> Triple(AI, if (a.id == "analysis") R.drawable.ic_analysis else R.drawable.ic_context, Ink.label(this, a)) }
                ?: Triple(AI, R.drawable.ic_analysis, getString(R.string.tab_ai_card)).takeIf { actions.isNotEmpty() })
        val n = tabs.size + if (prefs.cardSave) 4 else 2; val gap = Ink.dp(this, 5) // + save, Notes, back, close
        val side = minOf(Ink.dp(this, 44), (resources.displayMetrics.widthPixels * prefs.cardWidth / 100 - 2 * pad - gap * (n + 3)) / n)
        val bar = Ink.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        fun add(v: View, first: Boolean = false) = bar.addView(v, Ink.lp(android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { if (!first) leftMargin = gap })
        tabs.forEachIndexed { i, (id, icon, label) ->
            val isAi = id == AI
            // "AI" in the corner: the AI tab, and Translate when it holds AI translation.
            add(iconButton(icon, label, side, ai = isAi || id == TRANSLATE && aiTranslate) { if (isAi) launch { val r = withContext(Dispatchers.IO) { actions.map { answers.block(ai.ask(it.id)) } }
                if (r.all { it != null }) toast(getString(r.first()!!)) else open(id) } else open(id) }.also { tabButtons[id] = it }, i == 0)
        }
        // The AI tab is dimmed while none of its modules can answer; a saved answer still opens offline.
        launch { val blocked = withContext(Dispatchers.IO) { actions.map { answers.block(ai.ask(it.id)) } }
            tabButtons[AI]?.alpha = if (blocked.all { it != null }) Ink.OFF else 1f }
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
        // Held: the app itself (its search field, the settings) — the card goes.
        add(iconButton(R.drawable.ic_close, getString(R.string.close), side) { finish() }.apply { setOnLongClickListener {
            startActivity(Intent(this@CardActivity, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)); finish(); true } })
        launch { if (withContext(Dispatchers.IO) { runCatching { isSaved() }.getOrDefault(false) }) starred(true) }

        // No icon bar (Card → Opening): the card is the open page only; Back or a tap outside closes it.
        // The line over the icons; by pages, a segment per page, the open one thick. Without the icons: by pages only.
        pageLine = PageLine(this, solid = prefs.cardBar)
        if (prefs.cardBar || prefs.cardPaged) card.addView(pageLine, Ink.lp(h = Ink.dp(this, 4)).apply { topMargin = Ink.dp(this@CardActivity, 6); bottomMargin = Ink.dp(this@CardActivity, 6) })
        if (prefs.cardBar) card.addView(bar)
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
        if (fill) when (tab) { DICT -> dictionary.fill(p); SAVED -> words.fill(p); TRANSLATE -> translate.fill(p); else -> ai.fill(p) }
    }

    private lateinit var hintView: TextView
    private lateinit var pageLine: PageLine

    /** A line, or by pages its segments: [pages] of them, [at] (0-based) thick; [solid]: a plain line when there are no pages. */
    private class PageLine(ctx: android.content.Context, private val solid: Boolean) : View(ctx) {
        var pages = 0; var at = 0
        private val paint = android.graphics.Paint().apply { color = Ink.fg(ctx) }
        override fun onDraw(c: android.graphics.Canvas) {
            val thin = Ink.dp(context, 1).toFloat(); val mid = height / 2f
            if (pages < 2) { if (solid) c.drawRect(0f, mid - thin / 2, width.toFloat(), mid + thin / 2, paint); return }
            val gap = Ink.dp(context, 6).toFloat(); val w = (width - gap * (pages - 1)) / pages
            for (i in 0 until pages) { val x = i * (w + gap); val h = if (i == at) height.toFloat() else thin
                c.drawRect(x, mid - h / 2, x + w, mid + h / 2, paint) }
        }
    }

    /** A page of the content: the card's height less two lines kept from the page before. */
    private fun pageStep() = (scroll.height - (size * 2.6f * resources.displayMetrics.scaledDensity).toInt()).coerceAtLeast(scroll.height / 2)

    /** [y] in the page moved up to the top of the text line it cuts: a page starts with a whole line. */
    private fun lineStart(y: Int): Int {
        fun find(v: View, top: Int): Int? {
            if (v is TextView) v.layout?.let { l -> val t = top + v.totalPaddingTop; return t + l.getLineTop(l.getLineForVertical(y - t)) }
            if (v is android.view.ViewGroup) for (i in 0 until v.childCount) { val c = v.getChildAt(i); val t = top + c.top
                if (y >= t && y < t + c.height) return find(c, t) }
            return null
        }
        return scroll.getChildAt(0)?.let { find(it, 0) } ?: y
    }

    /** Where the pages before this one started: back is exactly where it was. */
    private val pagesBefore = ArrayList<Int>()

    /** By pages: the next page (from the line the old one cut, two lines kept) or the one before. */
    private fun turnPage(next: Boolean) {
        val max = ((scroll.getChildAt(0)?.height ?: 0) - scroll.height).coerceAtLeast(0); val y = scroll.scrollY
        if (next) { if (y >= max) return
            pagesBefore += y
            scroll.scrollTo(0, nextPage(y, max))
        } else { if (y <= 0) return; scroll.scrollTo(0, pagesBefore.removeLastOrNull() ?: 0) }
    }

    private fun nextPage(y: Int, max: Int) = minOf(y + pageStep(), max).let { to -> lineStart(to).takeIf { it > y } ?: to }

    /** The page shown and how many there are, by pages only. */
    private fun showPage() {
        val content = scroll.getChildAt(0)?.height ?: 0; val step = pageStep()
        if (scroll.scrollY == 0) pagesBefore.clear()
        if (content <= scroll.height || step <= 0) { pageLine.pages = 0; pageLine.invalidate(); return }
        // The pages left, counted by the same turns as [turnPage] (each starts at a whole line).
        val max = content - scroll.height; var y = scroll.scrollY; var left = 0
        while (y < max) { y = nextPage(y, max); left++ }
        val at = pagesBefore.size + 1
        pageLine.pages = at + left; pageLine.at = at - 1; pageLine.invalidate()
    }
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
        if (!closeTitle() && !back()) @Suppress("DEPRECATION") super.onBackPressed()
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
        if (prefs.history) launch(Dispatchers.IO) { runCatching { store.meaning(query, kindName, first.line, prefs.answerLang) } }
        titleView.text = first.header; fitTitle()
        // How common the word is and how often it was looked up here, in any of its forms.
        seenJob?.join()
        val (rank, looks) = withContext(Dispatchers.IO) { runCatching {
            prefs.library().rank(first.word, first.book) to store.run { if (prefs.history) base(query, kindName, first.word); looks(first.word) }
        }.getOrDefault(0 to 0) }
        val facts = listOfNotNull(Facts.frequency(this@CardActivity, rank), Facts.looks(this@CardActivity, looks)).joinToString(" · ")
        // The selected form (the other dictionaries that know the word are in the Dictionary tab).
        val sub = listOfNotNull(if (first.word.equals(query, true)) null else getString(R.string.form_of_sel, query), facts.ifBlank { null }).joinToString("\n")
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
        const val DICT = "dict"; const val SAVED = "saved"; const val TRANSLATE = "translate"; const val AI = "ai"
        /** Border of the open tab: a little heavier than the others, not bold. */
        const val SELECTED = 4
    }

    /** A tap beside a selection ends it and its menu. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean {
        TextActions.outside(ev); swipe.onTouchEvent(ev); return super.dispatchTouchEvent(ev)
    }

    /** Full articles: a swipe left or right turns to the next or previous dictionary. */
    private val swipe by lazy { android.view.GestureDetector(this, object : android.view.GestureDetector.SimpleOnGestureListener() {
        override fun onFling(e1: android.view.MotionEvent?, e2: android.view.MotionEvent, vx: Float, vy: Float): Boolean {
            // By pages: up or down a page at once (no scroll on the way: the screen is drawn once).
            val inTitle = e1 != null && titleOver?.let { o -> android.graphics.Rect().also { o.getGlobalVisibleRect(it) }.contains(e1.x.toInt(), e1.y.toInt()) } == true
            if (prefs.cardPaged && !TextActions.selecting && !inTitle && kotlin.math.abs(vy) > 2 * kotlin.math.abs(vx) && kotlin.math.abs(vy) > 500) {
                turnPage(vy < 0); return true
            }
            val turn = dictionary.turn ?: return false
            if (shown != DICT || kotlin.math.abs(vx) < 2 * kotlin.math.abs(vy) || kotlin.math.abs(vx) < 500) return false
            turn(if (vx < 0) 1 else -1); return true
        }
    }) }
}
