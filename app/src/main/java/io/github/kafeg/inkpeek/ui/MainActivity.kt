package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.stardict.StarDict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Settings in four sections (Languages, Card, AI, Notes), each with three tabs; one class per section. */
class MainActivity : Activity(), CoroutineScope by MainScope() {
    lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var version: TextView
    /** The download dialog, for an app update started from About. */
    private val download = Rows.JobDialog(this)
    private lateinit var scroll: ScrollView
    private val sections = LinkedHashMap<Int, LinearLayout>()
    private val sectionButtons = HashMap<Int, TextView>()
    private val ui = Handler(Looper.getMainLooper())
    /** The open sub-tab of each section ("lang" → "other"), kept over a recreation. */
    val open = HashMap<String, String>()
    private lateinit var languages: LanguagesSettings
    private lateinit var words: WordsSettings
    private lateinit var card: CardSettings

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Ink.localized(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        Ink.theme(this)
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        savedInstanceState?.getStringArray("open")?.forEach { open[it.substringBefore('=')] = it.substringAfter('=') }
        // "lang:other", "card:look": a section and its tab straight away (scripts/screens.sh).
        val asked = intent.getStringExtra("open")?.split(':')
        asked?.getOrNull(1)?.let { open[asked[0]] = it }
        val pad = Ink.dp(this, 16)
        val col = Ink.vertical(this).apply {
            setPadding(pad, pad, pad, pad); isFocusableInTouchMode = true; setOnClickListener { hideKeyboard() }
        }
        scroll = ScrollView(this).apply { addView(col) }
        setContentView(scroll)
        col.requestFocus()

        col.addView(Ink.text(this, getString(R.string.app_name), 26f, bold = true))
        // The status line, the version on its right (a tap opens About; "0.5.112 → 0.5.118" when a newer one is out).
        status = Ink.text(this, "", 14f)
        version = Ink.text(this, "", 14f).apply { setOnClickListener { About.show(this@MainActivity) }; val q = Ink.dp(this@MainActivity, 6); setPadding(q, q, 0, q) }
        col.addView(Ink.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL
            addView(status, Ink.lp(0, weight = 1f)); addView(version, Ink.lp(android.view.ViewGroup.LayoutParams.WRAP_CONTENT)) })
        val probe = edit(getString(R.string.try_hint), "")
        val tryRow = Ink.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        tryRow.addView(probe, Ink.lp(0, weight = 1f))
        fun openCard() { if (probe.text.isNotBlank()) startActivity(WordsList.card(this, probe.text.toString())) }
        probe.setOnEditorActionListener { _, _, _ -> hideKeyboard(); openCard(); true }
        // A magnifier, like any search field: it opens the card for the text (Enter does the same).
        tryRow.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search); setColorFilter(Ink.fg(this@MainActivity)); background = Ink.border(this@MainActivity); val q = Ink.dp(this@MainActivity, 10); setPadding(q, q, q, q)
            contentDescription = getString(R.string.look_up); tooltipText = contentDescription; setOnClickListener { openCard() }
        }, LinearLayout.LayoutParams(Ink.dp(this, 52), Ink.dp(this, 52)).apply { leftMargin = Ink.dp(this@MainActivity, 8) })
        col.addView(tryRow, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 8) })

        // Tabs with shrunk labels (a narrow screen) must not drop to a shared text baseline: their bottoms got cut.
        val bar = Ink.horizontal(this).apply { isBaselineAligned = false }
        listOf(R.string.tab_dicts, R.string.tab_card, R.string.tab_ai, R.string.tab_words).forEachIndexed { i, t ->
            sections[t] = Ink.vertical(this)
            val b = Ink.tab(this, getString(t), 18f) { show(t) }
            sectionButtons[t] = b
            // All as tall as the tallest, the text in the middle: a shrunk label does not stick to the top.
            bar.addView(b, Ink.lp(0, android.view.ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { if (i > 0) leftMargin = Ink.dp(this@MainActivity, 6) })
        }
        col.addView(bar, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 16) })
        // One text size for all four: the size the longest label shrank to on this screen.
        bar.post { val min = sectionButtons.values.minOf { it.textSize }
            sectionButtons.values.forEach { it.setAutoSizeTextTypeWithDefaults(TextView.AUTO_SIZE_TEXT_TYPE_NONE); it.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, min) } }
        sections.values.forEach { col.addView(it, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 12) }) }
        languages = LanguagesSettings(this).also { it.build(sections.getValue(R.string.tab_dicts)) }
        AiSettings(this).build(sections.getValue(R.string.tab_ai))
        card = CardSettings(this).also { it.build(sections.getValue(R.string.tab_card)) }
        words = WordsSettings(this).also { it.build(sections.getValue(R.string.tab_words)) }
        val section = mapOf("lang" to R.string.tab_dicts, "ai" to R.string.tab_ai, "card" to R.string.tab_card, "words" to R.string.tab_words)
        show(savedInstanceState?.getInt("tab") ?: asked?.let { section[it[0]] } ?: R.string.tab_dicts)
    }

    private var current = 0
    private fun show(tab: Int) {
        current = tab
        sections.forEach { (t, v) -> v.visibility = if (t == tab) View.VISIBLE else View.GONE }
        sectionButtons.forEach { (t, b) -> b.background = Ink.border(this, if (t == tab) 7 else 2) }
        if (tab == R.string.tab_words) words.refresh()
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out); out.putInt("tab", current); out.putStringArray("open", open.map { "${it.key}=${it.value}" }.toTypedArray())
    }
    override fun onResume() {
        super.onResume()
        resumed = true
        // Every progress step updates the dialog and the packs' line; the dictionaries are read again only at the end.
        Downloads.listener = { if (!isDestroyed) { download.update(); languages.refreshPacks(); if (!Downloads.busy) refreshDicts() } }
        if (Downloads.busy) download.show()
        Updates.pending?.let { Updates.pending = null; runCatching { startActivity(it) } }
        languages.refreshPacks(); refreshDicts(); card.refreshFont(); if (current == R.string.tab_words) words.refresh()
        // The daily update check (About), quietly: only the version line changes.
        if (Updates.due(this)) launch { withContext(Dispatchers.IO) { runCatching { Updates.check(this@MainActivity) } }; refreshStatus() }
    }
    override fun onPause() { TextActions.end(); resumed = false; ui.removeCallbacksAndMessages(null); Downloads.listener = null; super.onPause() }
    override fun onDestroy() { download.dismiss(); cancel(); super.onDestroy() }

    private var resumed = false
    private val poll = Runnable { refreshDicts() }

    @Deprecated("Plain Activity result: no AndroidX dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode == RESULT_OK && data != null) languages.onResult(requestCode, data)
    }

    /** Builds the dictionaries' indexes (in the background), the status line and the Other dictionaries list. */
    fun refreshDicts() {
        launch {
            val lib = prefs.library()
            val (found, known) = withContext(Dispatchers.IO) { lib.found().also { lib.prepareAll() } to
                LanguagesSettings.KNOWN.map { File(Environment.getExternalStorageDirectory(), it) }.filter { StarDict.scan(it).isNotEmpty() } }
            languages.refreshOther(lib, found, known)
            refreshStatus()
            // One poll while indexes are built, none after the screen is left.
            ui.removeCallbacks(poll); if (lib.busy && resumed) ui.postDelayed(poll, 3000)
        }
    }

    fun aiChanged() = card.syncAi()

    fun showDownload() = download.show()

    /** "Dictionaries: 9 · AI: Claude". */
    fun refreshStatus() {
        launch {
            val lib = prefs.library()
            val n = withContext(Dispatchers.IO) { lib.all().size }
            val busy = lib.status.values.count { it == "indexing" }
            val ai = if (prefs.aiOn) prefs.provider.title else getString(R.string.ai_none)
            status.text = if (busy > 0) getString(R.string.status_busy, n, busy, ai) else getString(R.string.status, n, ai)
            val newer = Updates.newer(this@MainActivity)
            version.text = Updates.versionName(this@MainActivity).let { v -> if (newer == null) v
                else android.text.SpannableString("$v → ${newer.name}").apply { setSpan(android.text.style.UnderlineSpan(), 0, length, 0) } }
            version.text = Ink.arrows(this@MainActivity, version.text, version.textSize)
        }
    }

    fun scrollTo(v: View) = scroll.post {
        val r = android.graphics.Rect(); v.getDrawingRect(r); scroll.offsetDescendantRectToMyCoords(v, r); scroll.scrollTo(0, r.top)
    }

    fun langName(code: String) = Ink.lang(this, code)

    /** A path on shared storage as people see it: "koreader/data/dict". */
    fun short(f: File) = f.path.removePrefix(Environment.getExternalStorageDirectory().path + "/")

    fun edit(hint: String, value: String, secret: Boolean = false, save: ((String) -> Unit)? = null) =
        EditText(this).apply {
            this.hint = hint; setText(value); textSize = 17f; setSingleLine()
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { v, _, _ -> save?.invoke(text.toString()); hideKeyboard(); v.clearFocus(); true }
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
            if (save != null) setOnFocusChangeListener { _, has -> if (!has) save(text.toString()) }
        }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(window.decorView.windowToken, 0)
        currentFocus?.clearFocus(); window.decorView.findFocus()?.clearFocus()
    }

    fun toast(s: String) = Ink.toast(this, s)

    /** A tap beside a selection ends it and its menu. */
    override fun dispatchTouchEvent(ev: android.view.MotionEvent): Boolean { TextActions.outside(ev); return super.dispatchTouchEvent(ev) }
}
