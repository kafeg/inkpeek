package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.Store
import io.github.kafeg.inkpeek.ai.Action
import io.github.kafeg.inkpeek.ai.Ai
import io.github.kafeg.inkpeek.ai.ModelInfo
import io.github.kafeg.inkpeek.ai.Provider
import io.github.kafeg.inkpeek.ai.Preset
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Settings in four tabs: dictionaries, AI, card, my words. */
class MainActivity : Activity(), CoroutineScope by MainScope() {
    private lateinit var prefs: Prefs
    private lateinit var status: TextView
    private val tabs = LinkedHashMap<Int, LinearLayout>()
    private val tabButtons = HashMap<Int, TextView>()
    private val ui = Handler(Looper.getMainLooper())
    private val sp = 18f

    override fun attachBaseContext(base: android.content.Context) = super.attachBaseContext(Ink.localized(base))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        val pad = Ink.dp(this, 16)
        val col = Ink.vertical(this).apply {
            setPadding(pad, pad, pad, pad); isFocusableInTouchMode = true; setOnClickListener { hideKeyboard() }
        }
        setContentView(ScrollView(this).apply { addView(col) })
        col.requestFocus()

        col.addView(Ink.text(this, getString(R.string.app_name), 26f, bold = true))
        status = Ink.text(this, "", 14f); col.addView(status)
        val probe = edit(getString(R.string.try_hint), "")
        val tryRow = Ink.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        tryRow.addView(probe, Ink.lp(0, weight = 1f))
        fun openCard() { if (probe.text.isNotBlank()) startActivity(Intent(this, CardActivity::class.java).setAction("colordict.intent.action.SEARCH")
            .putExtra("EXTRA_QUERY", probe.text.toString())) }
        probe.setOnEditorActionListener { _, _, _ -> hideKeyboard(); openCard(); true }
        // A magnifier, like any search field: it opens the card for the text (Enter does the same).
        tryRow.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_search); background = Ink.border(this@MainActivity); val q = Ink.dp(this@MainActivity, 10); setPadding(q, q, q, q)
            contentDescription = getString(R.string.look_up); tooltipText = contentDescription; setOnClickListener { openCard() }
        }, LinearLayout.LayoutParams(Ink.dp(this, 52), Ink.dp(this, 52)).apply { leftMargin = Ink.dp(this@MainActivity, 8) })
        col.addView(tryRow, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 8) })

        val bar = Ink.horizontal(this)
        listOf(R.string.tab_dicts, R.string.tab_ai, R.string.tab_card, R.string.tab_words).forEachIndexed { i, t ->
            tabs[t] = Ink.vertical(this)
            val b = Ink.button(this, getString(t), sp) { show(t) }
            tabButtons[t] = b
            bar.addView(b, Ink.lp(0, weight = 1f).apply { if (i > 0) leftMargin = Ink.dp(this@MainActivity, 6) })
        }
        col.addView(bar, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 16) })
        tabs.values.forEach { col.addView(it, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 12) }) }
        buildDictionaries(tabs.getValue(R.string.tab_dicts))
        buildAi(tabs.getValue(R.string.tab_ai))
        buildCard(tabs.getValue(R.string.tab_card))
        buildWords(tabs.getValue(R.string.tab_words))
        show(savedInstanceState?.getInt("tab") ?: R.string.tab_dicts)
    }

    private var current = 0
    private fun show(tab: Int) {
        current = tab
        tabs.forEach { (t, v) -> v.visibility = if (t == tab) View.VISIBLE else View.GONE }
        tabButtons.forEach { (t, b) -> b.background = Ink.border(this, if (t == tab) 7 else 2) }
        if (tab == R.string.tab_words) refreshWords()
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putInt("tab", current) }
    override fun onResume() { super.onResume(); refreshDicts(); if (current == R.string.tab_words) refreshWords() }
    override fun onPause() { ui.removeCallbacksAndMessages(null); super.onPause() }
    override fun onDestroy() { cancel(); super.onDestroy() }

    // --- Languages: the packs ---
    private lateinit var langBox: LinearLayout
    private var manifest: List<io.github.kafeg.inkpeek.packs.Component> = emptyList()
    private var manifestError: String? = null
    private var wantPairs: MutableSet<Pair<String, String>>? = null
    private var working = false

    private fun buildDictionaries(t: LinearLayout) {
        if (!Environment.isExternalStorageManager())
            t.addView(Ink.button(this, getString(R.string.allow_files, prefs.home.path), sp) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            })
        t.addView(Ink.text(this, getString(R.string.lang_about), 14f))
        langBox = Ink.vertical(this); t.addView(langBox, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 6) })
        loadManifest()
    }

    private fun loadManifest() {
        refreshLanguages()
        launch {
            val r = withContext(Dispatchers.IO) { runCatching { io.github.kafeg.inkpeek.packs.Packs.manifest() } }
            manifest = r.getOrDefault(emptyList())
            // No network, or the list is not there (HTTP 404 before the first release): say which.
            manifestError = r.exceptionOrNull()?.let { e -> if (e is java.net.UnknownHostException || e is java.net.SocketTimeoutException || e is java.net.ConnectException)
                getString(R.string.lang_no_net) else getString(R.string.lang_no_list, e.message ?: e.javaClass.simpleName) }
            refreshLanguages()
        }
    }

    private fun langName(code: String) = java.util.Locale(code).getDisplayLanguage(resources.configuration.locales[0]).replaceFirstChar { it.uppercase() }

    /** Every pack pair with its state and size; one button downloads what is ticked and removes what is not. */
    private fun refreshLanguages() {
        langBox.removeAllViews()
        val packs = prefs.packs; val installed = packs.installed()
        if (manifest.isEmpty()) {
            langBox.addView(Ink.text(this, manifestError ?: "…", 15f))
            langBox.addView(Ink.button(this, getString(R.string.lang_retry), sp) { loadManifest() })
            return
        }
        val P = io.github.kafeg.inkpeek.packs.Packs
        // The app's language first: those answers are the card's.
        val pairs = P.pairs(manifest).sortedWith(compareBy({ it.second != prefs.answerCode }, { langName(it.first) }, { langName(it.second) }))
        val want = wantPairs ?: packs.installedPairs().toMutableSet().also { wantPairs = it }
        pairs.forEach { pair ->
            val parts = P.needed(manifest, listOf(pair))
            val missing = parts.filter { installed[it.id] != it.sha256 }
            val state = when {
                missing.isEmpty() -> getString(R.string.lang_installed, parts.sumOf { it.size } / 1_000_000)
                missing.all { it.id in installed } -> getString(R.string.lang_update)
                else -> getString(R.string.lang_not_installed, missing.sumOf { it.size } / 1_000_000)
            }
            langBox.addView(CheckBox(this).apply {
                text = "${langName(pair.first)} → ${langName(pair.second)}  · $state"
                textSize = 16f; isChecked = pair in want; isEnabled = !working
                setOnCheckedChangeListener { _, on -> if (on) want += pair else want -= pair; refreshLanguages() }
            })
        }
        val needed = P.needed(manifest, want)
        val missing = needed.filter { installed[it.id] != it.sha256 }
        val keep = needed.map { it.id }.toSet()
        // What no ticked pair needs, and what the packs list no longer has (grammar before 21.10).
        val extra = manifest.filter { it.id in installed && it.id !in keep } +
            installed.keys.filter { id -> manifest.none { it.id == id } }.map { P.stale(it) }
        val label = when {
            missing.isNotEmpty() -> getString(R.string.lang_download, (missing.sumOf { it.size } / 1_000_000).coerceAtLeast(1))
            extra.isNotEmpty() -> getString(R.string.lang_remove)
            else -> getString(R.string.lang_ready)
        }
        val status = Ink.text(this, "", 14f)
        langBox.addView(Ink.button(this, label, sp) { if (!working && (missing.isNotEmpty() || extra.isNotEmpty())) apply(missing, extra, status) }
            .apply { alpha = if (missing.isEmpty() && extra.isEmpty() || working) 0.4f else 1f }, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 8) })
        langBox.addView(status)
        howPacks(langBox)
    }

    /** What is inside a pack and where it comes from. */
    private fun howPacks(box: LinearLayout) {
        box.addView(Ink.text(this, getString(R.string.how_title), 16f, bold = true), Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 20) })
        listOf(R.string.how_dict_t to R.string.how_dict, R.string.how_gram_t to R.string.how_gram, R.string.how_mt_t to R.string.how_mt,
            R.string.how_lid_t to R.string.how_lid, R.string.how_build_t to R.string.how_build).forEach { (t, d) ->
            box.addView(Ink.text(this, "<b>${Ink.escape(getString(t))}.</b> ${Ink.escape(getString(d))}", 14f, html = true),
                Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 6) })
        }
        box.addView(Ink.button(this, getString(R.string.how_more), 15f) {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/kafeg/inkpeek/blob/main/packs/README.md"))) }
                .onFailure { toast(it.message ?: "") }
        }, Ink.lp(android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { topMargin = Ink.dp(this@MainActivity, 10) })
    }

    private fun apply(missing: List<io.github.kafeg.inkpeek.packs.Component>, extra: List<io.github.kafeg.inkpeek.packs.Component>, status: TextView) {
        working = true; refreshLanguages()
        launch {
            val packs = prefs.packs
            val r = withContext(Dispatchers.IO) { runCatching {
                extra.forEach { packs.remove(it) }
                missing.forEachIndexed { i, c ->
                    packs.install(c) { done, all -> ui.post { statusLine?.text = getString(R.string.lang_working, i + 1, missing.size, c.id, (done * 100 / all.coerceAtLeast(1)).toInt()) } }
                }
            } }
            working = false; statusLine = null
            prefs.reload()
            r.onFailure { toast(getString(R.string.failed, it.message)) }
            refreshLanguages(); refreshDicts()
        }
        statusLine = status
    }
    private var statusLine: TextView? = null

    /** Builds the indexes of the pack dictionaries once (in the background) and shows the status line. */
    private fun refreshDicts() {
        launch {
            val lib = prefs.library()
            val all = withContext(Dispatchers.IO) { lib.all().also { lib.prepareAll() } }
            val busy = lib.status.values.count { it == "indexing" }
            status.text = if (busy > 0) getString(R.string.status_busy, all.size, busy, prefs.provider.title)
                else getString(R.string.status, all.size, prefs.provider.title)
            if (lib.busy) ui.postDelayed({ refreshDicts() }, 3000)
        }
    }

    // --- AI ---
    private fun buildAi(t: LinearLayout) {
        t.addView(switch(getString(R.string.use_ai), prefs.aiOn) { prefs.aiOn = it })
        t.addView(switch(getString(R.string.phrase_ai), prefs.phraseAutoAi) { prefs.phraseAutoAi = it })
        val providers = Provider.entries
        val keyInfo = Ink.text(this, "", 14f)
        val key = edit(getString(R.string.key_hint), "", secret = true)
        val model = Ink.button(this, "", sp) {}
        // Own server: a preset fills the address; only the IP of a local server needs changing.
        val server = Ink.vertical(this)
        val url = edit(getString(R.string.server_url), "") { prefs.setUrl(Provider.CUSTOM, it) }
        val presets = Preset.ALL
        server.addView(spinner(listOf(getString(R.string.preset_pick)) + presets.map { it.name }, 0) {
            if (it > 0) { url.setText(presets[it - 1].url); prefs.setUrl(Provider.CUSTOM, presets[it - 1].url) }
        })
        server.addView(url)
        fun load(p: Provider) {
            key.setText(prefs.key(p))
            server.visibility = if (p == Provider.CUSTOM) View.VISIBLE else View.GONE
            url.setText(prefs.url(Provider.CUSTOM))
            keyInfo.text = when (prefs.keySource(p)) {
                "typed in settings" -> getString(R.string.key_typed_ok)
                "none" -> if (p == Provider.CUSTOM) getString(R.string.key_optional, prefs.keyPath(p).path)
                    else getString(R.string.key_missing, prefs.keyPath(p).path)
                else -> getString(R.string.key_file_ok, prefs.keyPath(p).path)
            }
            model.text = getString(R.string.model, prefs.model(p).ifBlank { getString(R.string.model_choose) })
        }
        t.addView(spinner(providers.map { it.title }, providers.indexOf(prefs.provider)) { prefs.provider = providers[it]; load(providers[it]); refreshDicts() })
        t.addView(server)
        t.addView(key)
        t.addView(Ink.button(this, getString(R.string.save_key), sp) {
            prefs.setKey(prefs.provider, key.text.toString())
            if (prefs.provider == Provider.CUSTOM) prefs.setUrl(Provider.CUSTOM, url.text.toString())
            load(prefs.provider); toast(getString(R.string.saved))
        })
        t.addView(keyInfo, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 6) })
        model.setOnClickListener { chooseModel { load(prefs.provider) } }
        t.addView(model, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 12) })
        t.addView(switch(getString(R.string.mt_ai_first), prefs.translateMode == "ai") { prefs.translateMode = if (it) "ai" else "offline" },
            Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 12) })
        load(prefs.provider)
    }

    // --- My words ---
    private lateinit var wordsTabs: LinearLayout
    private lateinit var wordsList: LinearLayout
    private var listKind = "word"

    private fun buildWords(t: LinearLayout) {
        wordsTabs = Ink.vertical(this); t.addView(wordsTabs)
        wordsList = Ink.vertical(this); t.addView(wordsList, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 10) })
    }

    /** Saved words or phrases, or the history: tap opens the card (offline from saved answers), ✕ removes. */
    private fun refreshWords(): kotlinx.coroutines.Job = launch {
        val lang = prefs.answerLang; val kind = listKind
        val (items, counts) = withContext(Dispatchers.IO) { runCatching {
            val st = Store.get(this@MainActivity)
            (if (kind == "history") st.history(lang) else st.saved(kind, lang)) to
                listOf(st.count("saved", "word"), st.count("saved", "phrase"), st.count("history"))
        }.getOrNull() } ?: return@launch
        wordsTabs.removeAllViews()
        wordsTabs.addView(Ink.subTabs(this@MainActivity, listOf("word", "phrase", "history").zip(listOf(R.string.list_words, R.string.list_phrases, R.string.list_history))
            .mapIndexed { i, (k, label) -> k to getString(label) + " (" + counts[i] + ")" }, kind, 16f) { listKind = it; refreshWords() })
        wordsList.removeAllViews()
        if (items.isEmpty()) wordsList.addView(Ink.text(this@MainActivity,
            getString(if (kind == "history") R.string.list_empty_history else R.string.list_empty_saved), 16f))
        items.forEach { e ->
            val r = Ink.horizontal(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
            r.addView(Ink.entry(this@MainActivity, e.text, e.meaning, 16f).apply {
                val q = Ink.dp(this@MainActivity, 6); setPadding(0, q, q, q)
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, CardActivity::class.java).setAction("colordict.intent.action.SEARCH").putExtra("EXTRA_QUERY", e.text))
                }
            }, Ink.lp(0, weight = 1f))
            r.addView(Ink.button(this@MainActivity, "✕", 16f) {
                this@MainActivity.launch { withContext(Dispatchers.IO) { runCatching { Store.get(this@MainActivity).run { if (kind == "history") forget(e.text) else unsave(e.text) } } }; refreshWords() }
            })
            wordsList.addView(r)
        }
        // An action on the whole list: under it, on the right, apart from the tabs.
        if (kind == "history" && items.isNotEmpty()) wordsList.addView(Ink.button(this@MainActivity, getString(R.string.clear_history), 15f) {
            this@MainActivity.launch { withContext(Dispatchers.IO) { runCatching { Store.get(this@MainActivity).clearHistory() } }; refreshWords() }
        }, Ink.lp(android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { gravity = Gravity.END; topMargin = Ink.dp(this@MainActivity, 14) })
    }

    private fun chooseModel(done: () -> Unit) {
        val p = prefs.provider; toast(getString(R.string.loading_models))
        launch {
            val r = withContext(Dispatchers.IO) { runCatching { Ai.models(p, prefs.effectiveKey(p), prefs.url(p)) } }
            r.fold({ models -> pickModel(p, models, done) }, { toast(it.message ?: it.javaClass.simpleName) })
        }
    }

    /** Model list with a text filter, a maker filter, "free only" and sorting. */
    private fun pickModel(p: Provider, models: List<ModelInfo>, done: () -> Unit) {
        val box = Ink.vertical(this).apply { val q = Ink.dp(this@MainActivity, 12); setPadding(q, q, q, 0) }
        val filter = EditText(this).apply { hint = getString(R.string.filter); textSize = 16f; setSingleLine() }
        val makers = listOf(getString(R.string.all_publishers)) + models.map { it.publisher }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        var maker = 0; var sort = 0
        val freeOnly = CheckBox(this).apply { text = getString(R.string.free_only); textSize = 16f; visibility = if (models.any { it.free }) View.VISIBLE else View.GONE }
        val list = ListView(this)
        var shown = models
        fun label(m: ModelInfo) = m.id + when {
            m.free -> "  · " + getString(R.string.free)
            m.price != null -> "  · $%.2f/$%.2f".format(java.util.Locale.US, m.price.first, m.price.second)
            else -> ""
        } + if (m.context > 0) "  · ${m.context / 1000}k" else ""
        fun apply() {
            val f = filter.text.toString().trim().lowercase()
            shown = models.filter { m ->
                (!freeOnly.isChecked || m.free) && (maker == 0 || m.publisher == makers[maker]) &&
                    (f.isEmpty() || m.id.lowercase().contains(f) || m.name.lowercase().contains(f))
            }.let { l -> when (sort) { 1 -> l.sortedBy { it.price?.first ?: Double.MAX_VALUE }; 2 -> l.sortedByDescending { it.created }; else -> l.sortedBy { it.id } } }
            list.adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, shown.map(::label))
        }
        box.addView(filter)
        if (makers.size > 2) box.addView(spinner(makers, 0) { maker = it; apply() })
        val row = Ink.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(freeOnly, Ink.lp(0, weight = 1f))
        row.addView(spinner(listOf(getString(R.string.sort_name), getString(R.string.sort_price), getString(R.string.sort_new)), 0) { sort = it; apply() },
            Ink.lp(0, weight = 1f))
        box.addView(row)
        box.addView(list, Ink.lp(h = Ink.dp(this, 380)))
        apply()
        val dlg = AlertDialog.Builder(this).setTitle(getString(R.string.models_title, p.title, models.size)).setView(box)
            .setNegativeButton(R.string.cancel, null).show()
        filter.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = apply()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        freeOnly.setOnCheckedChangeListener { _, _ -> apply() }
        list.setOnItemClickListener { _, _, i, _ -> prefs.setModel(p, shown[i].id); dlg.dismiss(); done() }
    }

    // --- Card ---
    private fun buildCard(t: LinearLayout) {
        val codes = listOf("") + io.github.kafeg.inkpeek.Prefs.ANSWERS
        val names = listOf(getString(R.string.ui_lang_system)) + io.github.kafeg.inkpeek.Prefs.ANSWERS.map { c ->
            java.util.Locale(c).let { it.getDisplayLanguage(it) }.replaceFirstChar { it.uppercase() } }
        t.addView(Ink.text(this, getString(R.string.ui_lang), 14f))
        t.addView(spinner(names, codes.indexOf(prefs.uiLang).coerceAtLeast(0)) { prefs.uiLang = codes[it]; recreate() })
        t.addView(Ink.text(this, getString(R.string.icons_title), 18f, bold = true), Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 18) })
        // What each icon of the card does: icon, name, one line.
        val icons = listOf(Triple(R.drawable.ic_dictionary, getString(R.string.tab_dictionary), getString(R.string.icon_dict_d)),
            Triple(R.drawable.ic_translate, getString(R.string.tab_translate), getString(R.string.icon_translate_d))) +
            Action.ALL.map { a -> Triple(if (a.id == "analysis") R.drawable.ic_analysis else R.drawable.ic_context,
                Ink.label(this, a) + " · AI", Ink.description(this, a)) } +
            listOf(Triple(R.drawable.ic_star, getString(R.string.save_word), getString(R.string.icon_save_d)),
                Triple(R.drawable.ic_words, getString(R.string.tab_words), getString(R.string.icon_words_d)),
                Triple(R.drawable.ic_close, getString(R.string.close), getString(R.string.icon_close_d)))
        icons.forEach { (icon, label, about) ->
            val r = Ink.horizontal(this)
            r.addView(android.widget.ImageView(this).apply { setImageResource(icon) }, LinearLayout.LayoutParams(Ink.dp(this, 28), Ink.dp(this, 28)).apply { rightMargin = Ink.dp(this@MainActivity, 12) })
            r.addView(Ink.text(this, "<b>${Ink.escape(label)}</b><br>" + Ink.escape(about), 15f, html = true), Ink.lp(0, weight = 1f))
            t.addView(r, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 10) })
        }
    }

    // --- helpers ---
    private fun edit(hint: String, value: String, secret: Boolean = false, number: Boolean = false, save: ((String) -> Unit)? = null) =
        EditText(this).apply {
            this.hint = hint; setText(value); textSize = sp; setSingleLine()
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { v, _, _ -> save?.invoke(text.toString()); hideKeyboard(); v.clearFocus(); true }
            inputType = when { secret -> InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
                number -> InputType.TYPE_CLASS_NUMBER; else -> InputType.TYPE_CLASS_TEXT }
            if (save != null) setOnFocusChangeListener { _, has -> if (!has) save(text.toString()) }
        }

    private fun switch(label: String, value: Boolean, save: (Boolean) -> Unit) = Switch(this).apply {
        text = label; textSize = sp; isChecked = value; setOnCheckedChangeListener { _, v -> save(v) }
    }

    private fun spinner(items: List<String>, selected: Int, onPick: (Int) -> Unit) = Spinner(this).apply {
        adapter = ArrayAdapter(this@MainActivity, android.R.layout.simple_spinner_dropdown_item, items)
        setSelection(selected)
        onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            private var first = true
            override fun onItemSelected(a: AdapterView<*>?, v: View?, i: Int, id: Long) { if (first) { first = false; return }; onPick(i) }
            override fun onNothingSelected(a: AdapterView<*>?) {}
        }
    }

    private fun hideKeyboard() {
        (getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager)
            .hideSoftInputFromWindow(window.decorView.windowToken, 0)
        currentFocus?.clearFocus(); window.decorView.findFocus()?.clearFocus()
    }

    companion object {
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
