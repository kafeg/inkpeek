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
import android.widget.Toast
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

/** Settings in four sections (Languages, AI, Card, My words), each with three tabs; one class per section. */
class MainActivity : Activity(), CoroutineScope by MainScope() {
    lateinit var prefs: Prefs
    private lateinit var status: TextView
    private lateinit var scroll: ScrollView
    private val sections = LinkedHashMap<Int, LinearLayout>()
    private val sectionButtons = HashMap<Int, TextView>()
    private val ui = Handler(Looper.getMainLooper())
    /** The open sub-tab of each section ("lang" → "other"), kept over a recreation. */
    val open = HashMap<String, String>()
    private lateinit var languages: LanguagesSettings
    private lateinit var words: WordsSettings

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Ink.localized(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        savedInstanceState?.getStringArray("open")?.forEach { open[it.substringBefore('=')] = it.substringAfter('=') }
        val pad = Ink.dp(this, 16)
        val col = Ink.vertical(this).apply {
            setPadding(pad, pad, pad, pad); isFocusableInTouchMode = true; setOnClickListener { hideKeyboard() }
        }
        scroll = ScrollView(this).apply { addView(col) }
        setContentView(scroll)
        col.requestFocus()

        col.addView(Ink.text(this, getString(R.string.app_name), 26f, bold = true))
        status = Ink.text(this, "", 14f); col.addView(status)
        val probe = edit(getString(R.string.try_hint), "")
        val tryRow = Ink.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        tryRow.addView(probe, Ink.lp(0, weight = 1f))
        fun openCard() { if (probe.text.isNotBlank()) startActivity(WordsList.card(this, probe.text.toString())) }
        probe.setOnEditorActionListener { _, _, _ -> hideKeyboard(); openCard(); true }
        // A magnifier, like any search field: it opens the card for the text (Enter does the same).
        tryRow.addView(ImageView(this).apply {
            setImageResource(R.drawable.ic_search); background = Ink.border(this@MainActivity); val q = Ink.dp(this@MainActivity, 10); setPadding(q, q, q, q)
            contentDescription = getString(R.string.look_up); tooltipText = contentDescription; setOnClickListener { openCard() }
        }, LinearLayout.LayoutParams(Ink.dp(this, 52), Ink.dp(this, 52)).apply { leftMargin = Ink.dp(this@MainActivity, 8) })
        col.addView(tryRow, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 8) })

        // Tabs with shrunk labels (a narrow screen) must not drop to a shared text baseline: their bottoms got cut.
        val bar = Ink.horizontal(this).apply { isBaselineAligned = false }
        listOf(R.string.tab_dicts, R.string.tab_ai, R.string.tab_card, R.string.tab_words).forEachIndexed { i, t ->
            sections[t] = Ink.vertical(this)
            val b = Ink.tab(this, getString(t), 18f) { show(t) }
            sectionButtons[t] = b
            bar.addView(b, Ink.lp(0, weight = 1f).apply { if (i > 0) leftMargin = Ink.dp(this@MainActivity, 6) })
        }
        col.addView(bar, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 16) })
        sections.values.forEach { col.addView(it, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 12) }) }
        languages = LanguagesSettings(this).also { it.build(sections.getValue(R.string.tab_dicts)) }
        AiSettings(this).build(sections.getValue(R.string.tab_ai))
        CardSettings(this).build(sections.getValue(R.string.tab_card))
        words = WordsSettings(this).also { it.build(sections.getValue(R.string.tab_words)) }
        show(savedInstanceState?.getInt("tab") ?: R.string.tab_dicts)
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
        PackJob.listener = { if (!isDestroyed) { languages.refreshPacks(); refreshDicts() } }
        languages.refreshPacks(); refreshDicts(); if (current == R.string.tab_words) words.refresh()
    }
    override fun onPause() { ui.removeCallbacksAndMessages(null); PackJob.listener = null; super.onPause() }
    override fun onDestroy() { cancel(); super.onDestroy() }

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
            if (lib.busy) ui.postDelayed({ refreshDicts() }, 3000)
        }
    }

    /** "Dictionaries: 9 · AI: Claude". */
    fun refreshStatus() {
        launch {
            val lib = prefs.library()
            val n = withContext(Dispatchers.IO) { lib.all().size }
            val busy = lib.status.values.count { it == "indexing" }
            val ai = if (prefs.aiOn) prefs.provider.title else getString(R.string.ai_none)
            status.text = if (busy > 0) getString(R.string.status_busy, n, busy, ai) else getString(R.string.status, n, ai)
        }
    }

    fun scrollTo(v: View) = scroll.post {
        val r = android.graphics.Rect(); v.getDrawingRect(r); scroll.offsetDescendantRectToMyCoords(v, r); scroll.scrollTo(0, r.top)
    }

    fun langName(code: String) = java.util.Locale(code).getDisplayLanguage(resources.configuration.locales[0]).replaceFirstChar { it.uppercase() }

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

    fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
