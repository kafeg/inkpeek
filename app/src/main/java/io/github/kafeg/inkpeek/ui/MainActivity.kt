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
        prefs = Prefs(this); screen = java.lang.ref.WeakReference(this)
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
        fun openCard() { if (probe.text.isNotBlank()) startActivity(WordsList.card(this, probe.text.toString())) }
        probe.setOnEditorActionListener { _, _, _ -> hideKeyboard(); openCard(); true }
        // A magnifier, like any search field: it opens the card for the text (Enter does the same).
        tryRow.addView(android.widget.ImageView(this).apply {
            setImageResource(R.drawable.ic_search); background = Ink.border(this@MainActivity); val q = Ink.dp(this@MainActivity, 10); setPadding(q, q, q, q)
            contentDescription = getString(R.string.look_up); tooltipText = contentDescription; setOnClickListener { openCard() }
        }, LinearLayout.LayoutParams(Ink.dp(this, 52), Ink.dp(this, 52)).apply { leftMargin = Ink.dp(this@MainActivity, 8) })
        col.addView(tryRow, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 8) })

        // Tabs with shrunk labels (a narrow screen) must not drop to a shared text baseline: their bottoms got cut.
        val bar = Ink.horizontal(this).apply { isBaselineAligned = false }
        listOf(R.string.tab_dicts, R.string.tab_ai, R.string.tab_card, R.string.tab_words).forEachIndexed { i, t ->
            tabs[t] = Ink.vertical(this)
            val b = Ink.tab(this, getString(t), sp) { show(t) }
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
    private val working get() = install?.isActive == true

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
            val packs = prefs.packs
            val r = withContext(Dispatchers.IO) { runCatching { packs.list() } }
            // Offline: the list received last time, so what is installed can still be seen and removed.
            manifest = r.getOrNull() ?: withContext(Dispatchers.IO) { packs.cachedList() }.orEmpty()
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
        if (manifest.isEmpty() || manifestError != null) {
            langBox.addView(Ink.text(this, manifestError ?: "…", 15f))
            langBox.addView(Ink.button(this, getString(R.string.lang_retry), sp) { loadManifest() }, Ink.lp().apply { bottomMargin = Ink.dp(this@MainActivity, 8) })
            if (manifest.isEmpty()) return
        }
        val P = io.github.kafeg.inkpeek.packs.Packs
        // The app's language first: those answers are the card's.
        val pairs = P.pairs(manifest).sortedWith(compareBy({ it.second != prefs.answerCode }, { langName(it.first) }, { langName(it.second) }))
        // The ticks are saved at once: the card uses only the ticked pairs, the button downloads or frees space.
        val want = wantPairs ?: (prefs.pairs?.map { it.substringBefore('-') to it.substringAfter('-') } ?: packs.installedPairs()).toMutableSet().also { wantPairs = it }
        pairs.forEach { pair ->
            val parts = P.needed(manifest, listOf(pair))
            val missing = parts.filter { installed[it.id] != it.sha256 }
            val state = when {
                missing.isEmpty() -> getString(R.string.lang_installed, parts.sumOf { it.size } / 1_000_000)
                missing.all { it.id in installed } -> getString(R.string.lang_update)
                else -> getString(R.string.lang_not_installed, missing.sumOf { it.size } / 1_000_000)
            }
            langBox.addView(CheckBox(this).apply {
                // The pair, and under it its state in smaller type: no odd wrap on a narrow screen.
                text = android.text.SpannableStringBuilder("${langName(pair.first)} → ${langName(pair.second)}\n").apply {
                    val at = length; append(state)
                    setSpan(android.text.style.RelativeSizeSpan(0.8f), at, length, android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                textSize = 16f; isChecked = pair in want; isEnabled = !working
                setOnCheckedChangeListener { _, on -> if (on) want += pair else want -= pair
                    prefs.pairs = want.map { "${it.first}-${it.second}" }.toSet(); refreshLanguages(); refreshDicts() }
            }, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 4) })
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
        val status = Ink.text(this, if (working) installStatus else "", 14f).also { statusLine = it }
        langBox.addView(Ink.button(this, label, sp) { if (!working && (missing.isNotEmpty() || extra.isNotEmpty())) apply(missing, extra) }
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

    /** Downloads and removals run in the process, not in this screen: a recreated screen (language change) finds them. */
    private fun apply(missing: List<io.github.kafeg.inkpeek.packs.Component>, extra: List<io.github.kafeg.inkpeek.packs.Component>) {
        val packs = prefs.packs; val app = applicationContext
        val res = resources; val main = Handler(Looper.getMainLooper()) // the process scope must not hold this activity
        install = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            val r = runCatching {
                extra.forEach { packs.remove(it) }
                missing.forEachIndexed { i, c ->
                    var shown = -1
                    packs.install(c) { done, all ->
                        val pct = (done * 100 / all.coerceAtLeast(1)).toInt()
                        // E-ink: redraw only when the percentage changes.
                        if (pct != shown) { shown = pct; installStatus = res.getString(R.string.lang_working, i + 1, missing.size, c.id, pct)
                            main.post { screen?.get()?.statusLine?.text = installStatus } }
                    }
                }
            }
            Prefs(app).reload()
            withContext(Dispatchers.Main) { install = null; screen?.get()?.installed(r.exceptionOrNull()) }
        }
        refreshLanguages()
    }

    private fun installed(e: Throwable?) {
        if (isDestroyed) return
        e?.let { toast(getString(R.string.failed, it.message)) }
        refreshLanguages(); refreshDicts()
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
            wordsList.addView(WordsList.row(this@MainActivity, e, 16f, { startActivity(WordsList.card(this@MainActivity, e.text)) }) {
                this@MainActivity.launch { withContext(Dispatchers.IO) { runCatching { Store.get(this@MainActivity).run { if (kind == "history") forget(e.text) else unsave(e.text) } } }; refreshWords() }
            })
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
    /** Card: Main (language, phrases, what each icon does) and Look (size, place, text, dark, icon bar). */
    private fun buildCard(t: LinearLayout) {
        val tabsBox = Ink.vertical(this); val main = Ink.vertical(this); val look = Ink.vertical(this)
        t.addView(tabsBox); t.addView(main); t.addView(look)
        fun open(k: String) {
            main.visibility = if (k == "main") View.VISIBLE else View.GONE; look.visibility = if (k == "look") View.VISIBLE else View.GONE
            tabsBox.removeAllViews()
            tabsBox.addView(Ink.subTabs(this, listOf("main" to getString(R.string.card_main), "look" to getString(R.string.card_look)), k, 16f) { open(it) },
                Ink.lp().apply { bottomMargin = Ink.dp(this@MainActivity, 8) })
        }
        val codes = listOf("") + io.github.kafeg.inkpeek.Prefs.ANSWERS
        val names = listOf(getString(R.string.ui_lang_system)) + io.github.kafeg.inkpeek.Prefs.ANSWERS.map { c ->
            java.util.Locale(c).let { it.getDisplayLanguage(it) }.replaceFirstChar { it.uppercase() } }
        main.addView(choice(getString(R.string.ui_lang), getString(R.string.ui_lang_d), names, codes.indexOf(prefs.uiLang).coerceAtLeast(0)) { prefs.uiLang = codes[it]; recreate() })
        main.addView(check(getString(R.string.phrase_ai), getString(R.string.phrase_ai_d), prefs.phraseAutoAi) { prefs.phraseAutoAi = it })
        main.addView(Ink.text(this, getString(R.string.icons_title), 18f, bold = true), Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 18) })
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
            main.addView(r, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 10) })
        }

        val heights = listOf(0, 50, 60, 70, 80, 90)
        look.addView(choice(getString(R.string.look_height), getString(R.string.look_height_d),
            heights.map { if (it == 0) getString(R.string.look_height_auto) else "$it %" }, heights.indexOf(prefs.cardHeight).coerceAtLeast(0)) { prefs.cardHeight = heights[it] })
        val widths = listOf(70, 80, 92, 100)
        look.addView(choice(getString(R.string.look_width), getString(R.string.look_width_d), widths.map { "$it %" },
            widths.indexOf(prefs.cardWidth).coerceAtLeast(0)) { prefs.cardWidth = widths[it] })
        val places = listOf("center", "top", "bottom", "reader")
        look.addView(choice(getString(R.string.look_place), getString(R.string.look_place_d),
            listOf(R.string.place_center, R.string.place_top, R.string.place_bottom, R.string.place_reader).map { getString(it) },
            places.indexOf(prefs.cardPlace).coerceAtLeast(0)) { prefs.cardPlace = places[it] })
        val sizes = listOf(16, 18, 20, 22, 24, 26)
        look.addView(choice(getString(R.string.look_text), getString(R.string.look_text_d), sizes.map { "$it" },
            sizes.indexOf(prefs.cardText).coerceAtLeast(0)) { prefs.cardText = sizes[it] })
        look.addView(check(getString(R.string.look_dark), getString(R.string.look_dark_d), prefs.cardDark) { prefs.cardDark = it })
        look.addView(check(getString(R.string.look_bar), getString(R.string.look_bar_d), prefs.cardBar) { prefs.cardBar = it })
        open("main")
    }

    /** A setting in the one style: a box on the left, its title and one small line on the right (a tap anywhere toggles). */
    private fun check(title: String, about: String, on: Boolean, save: (Boolean) -> Unit) = Ink.horizontal(this).apply {
        val box = CheckBox(this@MainActivity).apply { isChecked = on; setOnCheckedChangeListener { _, v -> save(v) } }
        addView(box, Ink.lp(android.view.ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = Ink.dp(this@MainActivity, 6) })
        addView(Ink.vertical(this@MainActivity).apply {
            addView(Ink.text(this@MainActivity, title, 16f, bold = true)); addView(Ink.text(this@MainActivity, about, 13f))
            setOnClickListener { box.toggle() }
        }, Ink.lp(0, weight = 1f))
        layoutParams = Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 14) }
    }

    /** A setting with a choice, in the one style: its title and one small line, the drop-down under them. */
    private fun choice(title: String, about: String, items: List<String>, selected: Int, pick: (Int) -> Unit) = Ink.vertical(this).apply {
        addView(Ink.text(this@MainActivity, title, 16f, bold = true)); addView(Ink.text(this@MainActivity, about, 13f))
        addView(spinner(items, selected, pick))
        layoutParams = Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 14) }
    }

    // --- helpers ---
    private fun edit(hint: String, value: String, secret: Boolean = false, save: ((String) -> Unit)? = null) =
        EditText(this).apply {
            this.hint = hint; setText(value); textSize = sp; setSingleLine()
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_DONE
            setOnEditorActionListener { v, _, _ -> save?.invoke(text.toString()); hideKeyboard(); v.clearFocus(); true }
            inputType = if (secret) InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD else InputType.TYPE_CLASS_TEXT
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
        /** One install at a time for the whole process, and the screen showing it (recreated on a language change). */
        private var install: kotlinx.coroutines.Job? = null
        @Volatile private var installStatus = ""
        private var screen: java.lang.ref.WeakReference<MainActivity>? = null
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
