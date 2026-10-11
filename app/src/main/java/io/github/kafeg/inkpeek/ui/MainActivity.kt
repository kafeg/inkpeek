package io.github.kafeg.inkpeek.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
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
import io.github.kafeg.inkpeek.ai.Action
import io.github.kafeg.inkpeek.ai.Ai
import io.github.kafeg.inkpeek.ai.ModelInfo
import io.github.kafeg.inkpeek.ai.Provider
import io.github.kafeg.inkpeek.stardict.Importer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Settings in three tabs: dictionaries, AI, card. */
class MainActivity : Activity(), CoroutineScope by MainScope() {
    private lateinit var prefs: Prefs
    private lateinit var dictList: LinearLayout
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
        tryRow.addView(Ink.button(this, getString(R.string.look_up), sp) {
            startActivity(Intent(this, CardActivity::class.java).setAction("colordict.intent.action.SEARCH")
                .putExtra("EXTRA_QUERY", probe.text.toString()))
        })
        col.addView(tryRow, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 8) })

        val bar = Ink.horizontal(this)
        listOf(R.string.tab_dicts, R.string.tab_ai, R.string.tab_card).forEachIndexed { i, t ->
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
        show(savedInstanceState?.getInt("tab") ?: R.string.tab_dicts)
    }

    private var current = 0
    private fun show(tab: Int) {
        current = tab
        tabs.forEach { (t, v) -> v.visibility = if (t == tab) View.VISIBLE else View.GONE }
        tabButtons.forEach { (t, b) -> b.background = Ink.border(this, if (t == tab) 7 else 2) }
    }

    override fun onSaveInstanceState(out: Bundle) { super.onSaveInstanceState(out); out.putInt("tab", current) }
    override fun onResume() { super.onResume(); refreshDicts() }
    override fun onPause() { ui.removeCallbacksAndMessages(null); super.onPause() }
    override fun onDestroy() { cancel(); super.onDestroy() }

    // --- Dictionaries ---
    private fun buildDictionaries(t: LinearLayout) {
        if (!Environment.isExternalStorageManager())
            t.addView(Ink.button(this, getString(R.string.allow_files, prefs.home.path), sp) {
                startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:$packageName")))
            })
        dictList = Ink.vertical(this); t.addView(dictList)
        val row = Ink.horizontal(this)
        row.addView(Ink.button(this, getString(R.string.import_file), sp) { pickFiles() }, Ink.lp(0, weight = 1f))
        row.addView(Ink.button(this, getString(R.string.get_dicts), sp) { startActivity(Intent(this, CatalogActivity::class.java)) },
            Ink.lp(0, weight = 1f).apply { leftMargin = Ink.dp(this@MainActivity, 8) })
        t.addView(row, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 10) })
        t.addView(Ink.text(this, getString(R.string.folder_note, prefs.dictsDir.path), 14f),
            Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 8) })
    }

    private fun refreshDicts() {
        launch {
            val lib = prefs.library()
            val dicts = withContext(Dispatchers.IO) { lib.all(prefs.order).also { lib.prepareAll() } }
            prefs.order = dicts.map { it.id }
            dictList.removeAllViews()
            if (dicts.isEmpty()) dictList.addView(Ink.text(this@MainActivity, getString(R.string.no_dicts), 16f))
            dicts.forEachIndexed { i, d ->
                val r = Ink.horizontal(this@MainActivity).apply { gravity = Gravity.CENTER_VERTICAL }
                val state = lib.status[d.id]
                r.addView(CheckBox(this@MainActivity).apply {
                    text = d.name + when { state == null -> ""; state == "indexing" -> "  · " + getString(R.string.st_indexing); else -> "  · $state" }
                    textSize = 16f; isChecked = d.id !in prefs.disabled
                    setOnCheckedChangeListener { _, on -> prefs.disabled = if (on) prefs.disabled - d.id else prefs.disabled + d.id }
                }, Ink.lp(0, weight = 1f))
                r.addView(Ink.button(this@MainActivity, "↑", 16f) { move(i, -1) })
                r.addView(Ink.button(this@MainActivity, "↓", 16f) { move(i, 1) })
                r.addView(Ink.button(this@MainActivity, "✕", 16f) { delete(d.name, d.ifo.parentFile!!) })
                dictList.addView(r)
            }
            val busy = lib.status.values.count { it == "indexing" }
            status.text = if (busy > 0) getString(R.string.status_busy, dicts.size, busy, prefs.provider.title)
                else getString(R.string.status, dicts.size, prefs.provider.title)
            if (lib.busy) ui.postDelayed({ refreshDicts() }, 3000)
        }
    }

    private fun move(i: Int, by: Int) {
        val o = prefs.order.toMutableList(); val j = i + by
        if (j !in o.indices) return
        o[i] = o[j].also { o[j] = o[i] }; prefs.order = o; refreshDicts()
    }

    private fun delete(name: String, dir: File) = AlertDialog.Builder(this).setMessage(getString(R.string.delete_q, name))
        .setPositiveButton(R.string.delete) { _, _ -> dir.deleteRecursively(); refreshDicts() }.setNegativeButton(R.string.cancel, null).show()

    private fun pickFiles() = startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
        .setType("*/*").putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true), 1)

    @Deprecated("Plain Activity result: no AndroidX dependency")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != 1 || resultCode != RESULT_OK || data == null) return
        val uris = data.clipData?.let { c -> (0 until c.itemCount).map { c.getItemAt(it).uri } } ?: listOfNotNull(data.data)
        status.text = getString(R.string.importing)
        launch {
            val r = withContext(Dispatchers.IO) {
                runCatching {
                    val loose = File(cacheDir, "loose-" + System.nanoTime()).apply { mkdirs() }
                    val dirs = uris.flatMap { u ->
                        val name = displayName(u)
                        contentResolver.openInputStream(u)!!.use { input ->
                            if (Regex("\\.(ifo|idx|idx\\.gz|dict|dict\\.dz|syn)$").containsMatchIn(name)) {
                                File(loose, name).outputStream().use { input.copyTo(it) }; emptyList()
                            } else Importer.importStream(name, input, prefs.dictsDir, cacheDir)
                        }
                    } + Importer.collect(loose, prefs.dictsDir)
                    loose.deleteRecursively(); dirs.size
                }
            }
            r.fold({ toast(getString(R.string.imported, it)) }, { toast(getString(R.string.import_failed, it.message)) })
            refreshDicts()
        }
    }

    private fun displayName(u: Uri): String = contentResolver.query(u, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { c -> if (c.moveToFirst()) c.getString(0) else null } ?: u.lastPathSegment.orEmpty()

    // --- AI ---
    private fun buildAi(t: LinearLayout) {
        t.addView(switch(getString(R.string.use_ai), prefs.aiOn) { prefs.aiOn = it })
        t.addView(switch(getString(R.string.phrase_ai), prefs.phraseAutoAi) { prefs.phraseAutoAi = it })
        val providers = Provider.entries
        val keyInfo = Ink.text(this, "", 14f)
        val key = edit(getString(R.string.key_hint), "", secret = true)
        val model = Ink.button(this, "", sp) {}
        fun load(p: Provider) {
            key.setText(prefs.key(p))
            keyInfo.text = when (prefs.keySource(p)) {
                "typed in settings" -> getString(R.string.key_typed_ok)
                "none" -> getString(R.string.key_missing, prefs.keyPath(p).path)
                else -> getString(R.string.key_file_ok, prefs.keyPath(p).path)
            }
            model.text = getString(R.string.model, prefs.model(p).ifBlank { getString(R.string.model_choose) })
        }
        t.addView(spinner(providers.map { it.title }, providers.indexOf(prefs.provider)) { prefs.provider = providers[it]; load(providers[it]); refreshDicts() })
        t.addView(key)
        t.addView(Ink.button(this, getString(R.string.save_key), sp) { prefs.setKey(prefs.provider, key.text.toString()); load(prefs.provider); toast(getString(R.string.saved)) })
        t.addView(keyInfo, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 6) })
        model.setOnClickListener { chooseModel { load(prefs.provider) } }
        t.addView(model, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 12) })
        t.addView(Ink.text(this, getString(R.string.answer_lang_t), 14f), Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 12) })
        val langs = (LANGS + prefs.answerLang).distinct()
        val here = resources.configuration.locales[0]
        val shown = langs.map { name -> java.util.Locale.getAvailableLocales().firstOrNull { it.getDisplayLanguage(java.util.Locale.ENGLISH) == name }
            ?.getDisplayLanguage(here)?.replaceFirstChar { it.uppercase() } ?: name }
        t.addView(spinner(shown, langs.indexOf(prefs.answerLang)) { prefs.answerLang = langs[it] })
        load(prefs.provider)
    }

    private fun chooseModel(done: () -> Unit) {
        val p = prefs.provider; toast(getString(R.string.loading_models))
        launch {
            val r = withContext(Dispatchers.IO) { runCatching { Ai.models(p, prefs.effectiveKey(p)) } }
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
        val codes = listOf("") + UI_LANGS
        val names = listOf(getString(R.string.ui_lang_system)) + UI_LANGS.map { c ->
            java.util.Locale(c).let { it.getDisplayLanguage(it) }.replaceFirstChar { it.uppercase() } }
        t.addView(Ink.text(this, getString(R.string.ui_lang), 14f))
        t.addView(spinner(names, codes.indexOf(prefs.uiLang).coerceAtLeast(0)) { prefs.uiLang = codes[it]; recreate() })
        t.addView(Ink.text(this, getString(R.string.text_size), 14f))
        val sizeRow = Ink.horizontal(this).apply { gravity = Gravity.CENTER_VERTICAL }
        val sizeView = Ink.text(this, prefs.fontSp.toString(), sp, bold = true).apply { gravity = Gravity.CENTER }
        fun step(by: Int) { prefs.fontSp = (prefs.fontSp + by).coerceIn(12, 40); sizeView.text = prefs.fontSp.toString() }
        val w = Ink.dp(this, 56)
        sizeRow.addView(Ink.button(this, "−", sp) { step(-2) }.apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(w, w))
        sizeRow.addView(sizeView, LinearLayout.LayoutParams(w * 2, w))
        sizeRow.addView(Ink.button(this, "+", sp) { step(2) }.apply { setPadding(0, 0, 0, 0) }, LinearLayout.LayoutParams(w, w))
        t.addView(sizeRow)
        t.addView(switch(getString(R.string.dim), prefs.dim) { prefs.dim = it })
        val pos = listOf("center", "top", "bottom", "reader")
        val posLabels = listOf(R.string.pos_center, R.string.pos_top, R.string.pos_bottom, R.string.pos_reader).map { getString(it) }
        t.addView(spinner(posLabels, pos.indexOf(prefs.position).coerceAtLeast(0)) { prefs.position = pos[it] })
        t.addView(Ink.text(this, getString(R.string.card_tabs), 18f, bold = true), Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 18) })
        Action.ALL.forEach { a ->
            t.addView(CheckBox(this).apply {
                text = Ink.label(this@MainActivity, a); textSize = sp; isChecked = a.id in prefs.actionIds
                setOnCheckedChangeListener { _, on -> prefs.actionIds = if (on) prefs.actionIds + a.id else prefs.actionIds - a.id }
            }, Ink.lp().apply { topMargin = Ink.dp(this@MainActivity, 8) })
            t.addView(Ink.text(this, Ink.description(this, a), 14f).apply { setPadding(Ink.dp(this@MainActivity, 32), 0, 0, 0) })
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
        /** Answer languages by English name (what the prompt uses). */
        val LANGS = listOf("Russian", "English", "German", "Ukrainian", "Spanish", "French", "Italian", "Portuguese",
            "Polish", "Czech", "Dutch", "Turkish", "Indonesian", "Vietnamese", "Chinese", "Japanese", "Korean", "Hindi", "Arabic")
        /** App languages that have res/values-<code>; "in" is Android's code for Indonesian. */
        val UI_LANGS = listOf("en", "ru", "de", "uk", "es", "fr", "it", "pt", "pl", "cs", "nl", "tr", "in", "vi", "zh", "ja", "ko", "hi")
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_LONG).show()
}
