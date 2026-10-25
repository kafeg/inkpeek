package io.github.kafeg.inkpeek.ui

import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ListView
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.ai.Ai
import io.github.kafeg.inkpeek.ai.Ask
import io.github.kafeg.inkpeek.ai.ModelInfo
import io.github.kafeg.inkpeek.ai.Preset
import io.github.kafeg.inkpeek.ai.Provider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** AI: Connection (provider, server, key, model, the answers' language, the general style), Modules (what each AI tab asks for). */
class AiSettings(private val a: MainActivity) {
    private val prefs get() = a.prefs
    private lateinit var modules: LinearLayout
    private lateinit var styleRow: LinearLayout

    fun build(t: LinearLayout) {
        val main = Ink.vertical(a); modules = Ink.vertical(a)
        a.tabPick["ai"] = Rows.subTabs(a, t, listOf(Triple("connect", a.getString(R.string.ai_connect), main),
            Triple("modules", a.getString(R.string.ai_modules), modules)), a.open["ai"] ?: "connect") { a.open["ai"] = it }
        buildMain(main); refreshModules()
    }

    private fun buildMain(t: LinearLayout) {
        val providers = Provider.entries
        val setup = Ink.vertical(a) // the provider's server, key and model: hidden while AI is off
        val keyAbout = Ink.text(a, "", 13.5f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }
        val key = a.edit(a.getString(R.string.key_hint), "", secret = true)
        val model = Ink.button(a, "", Rows.BUTTON) {}
        // Own server: a preset fills the address; only the IP of a local server needs changing.
        val server = Ink.vertical(a)
        val url = a.edit(a.getString(R.string.server_url), "") { prefs.setUrl(Provider.CUSTOM, it) }
        val presets = Preset.ALL
        server.addView(Rows.choice(a, a.getString(R.string.server_t), a.getString(R.string.server_d), listOf(a.getString(R.string.preset_pick)) + presets.map { it.name }, 0) {
            if (it > 0) { url.setText(presets[it - 1].url); prefs.setUrl(Provider.CUSTOM, presets[it - 1].url) }
        })
        server.addView(url)
        fun load(p: Provider) {
            key.setText(prefs.key(p))
            server.visibility = if (p == Provider.CUSTOM) View.VISIBLE else View.GONE
            url.setText(prefs.url(Provider.CUSTOM))
            keyAbout.text = when (prefs.keySource(p)) {
                Prefs.KeyFrom.TYPED -> a.getString(R.string.key_typed_ok)
                Prefs.KeyFrom.NONE -> if (p == Provider.CUSTOM) a.getString(R.string.key_optional) else a.getString(R.string.key_missing, a.short(prefs.keyPath(p)))
                Prefs.KeyFrom.FILE -> a.getString(R.string.key_file_ok, a.short(prefs.keyPath(p)))
            }
            model.text = prefs.model(p).ifBlank { a.getString(R.string.model_choose) } + "  ▾"
        }
        // "Off" first: no AI icons in the card and no AI tab in Translate.
        t.addView(Rows.choice(a, a.getString(R.string.ai_title), a.getString(R.string.ai_title_d), listOf(a.getString(R.string.ai_none)) + providers.map { it.title },
            if (prefs.aiOn) providers.indexOf(prefs.provider) + 1 else 0) { i ->
            prefs.aiOn = i > 0; setup.visibility = if (i > 0) View.VISIBLE else View.GONE
            if (i > 0) { prefs.provider = providers[i - 1]; load(providers[i - 1]) }; a.refreshStatus()
        }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = Ink.dp(a, 4) })
        t.addView(setup); setup.visibility = if (prefs.aiOn) View.VISIBLE else View.GONE
        setup.addView(server)
        setup.addView(Ink.vertical(a).apply {
            addView(Rows.texts(a, a.getString(R.string.key_t), "")); addView(keyAbout); addView(key)
            layoutParams = Ink.lp().apply { topMargin = Ink.dp(a, 12) }
        })
        setup.addView(Rows.buttons(a, a.getString(R.string.save_key) to {
            prefs.setKey(prefs.provider, key.text.toString())
            if (prefs.provider == Provider.CUSTOM) prefs.setUrl(Provider.CUSTOM, url.text.toString())
            load(prefs.provider); a.toast(a.getString(R.string.saved))
        }))
        setup.addView(Rows.texts(a, a.getString(R.string.model_t), a.getString(R.string.model_d)).apply { layoutParams = Ink.lp().apply { topMargin = Ink.dp(a, 12) } })
        model.setOnClickListener { chooseModel { load(prefs.provider) } }
        setup.addView(model, Ink.lp().apply { topMargin = Ink.dp(a, 6) })
        // The answers' language is set in Languages → Translation; here it is said, with the way there.
        answersRow = Ink.vertical(a); setup.addView(answersRow); refreshAnswers()
        styleRow = Ink.vertical(a); setup.addView(styleRow); refreshStyle()
        load(prefs.provider)
    }

    private lateinit var answersRow: LinearLayout

    /** The answers' language, made again when AI shows (it is changed in Languages). */
    fun refreshAnswers() {
        if (!::answersRow.isInitialized) return
        answersRow.removeAllViews()
        answersRow.addView(Rows.info(a, null, a.getString(R.string.ans_row, Ink.lang(a, prefs.answerCode)), a.getString(R.string.ans_lang_d), page = true) {
            a.goTo(R.string.tab_dicts, "translation") }.apply { (layoutParams as LinearLayout.LayoutParams).topMargin = Ink.dp(a, 12) })
    }

    /** The general style of every answer, ✎ to edit it. */
    private fun refreshStyle() {
        styleRow.removeAllViews()
        styleRow.addView(Rows.info(a, null, a.getString(R.string.prompt_system_t), mark(prefs.prompt("system") != null, a.getString(R.string.prompt_system_d))) {
            prompts(a.getString(R.string.prompt_system_t), R.string.prompt_system_note, listOf(Triple("system", null, Ai.SYSTEM))) { refreshStyle() } })
    }

    private fun mark(edited: Boolean, s: String) = if (edited) a.getString(R.string.prompt_edited) + " · " + s else s

    // --- Modules ---
    /** Translation, Analysis, Context: each part with its box (a section with none ticked is off) and ✎; asking ahead. */
    private fun refreshModules() {
        val t = modules; t.removeAllViews()
        t.addView(Rows.heading(a, a.getString(R.string.prompt_quick_t), first = true))
        val quickEdited = Ask.QUICK.keys.any { prefs.prompt("${Ask.QUICK.section}.${it.name}") != null }
        t.addView(Rows.check(a, a.getString(R.string.ai_tr_t), mark(quickEdited, a.getString(R.string.ai_tr_d)), prefs.aiTranslate,
            edit = { prompts(a.getString(R.string.prompt_quick_t) + " · AI", R.string.prompt_keys,
                Ask.QUICK.keys.map { Triple("${Ask.QUICK.section}.${it.name}", it.name, it.about) }) { refreshModules() } }) { prefs.aiTranslate = it })
        listOf(Ask.ANALYSIS to R.string.a_analysis, Ask.CONTEXT to R.string.a_context).forEach { (ask, title) ->
            t.addView(Rows.heading(a, a.getString(title)))
            ask.keys.forEach { k ->
                val id = "${ask.section}.${k.name}"
                val (name, about) = KEYS[k.name] ?: return@forEach
                t.addView(Rows.check(a, a.getString(name), mark(prefs.prompt(id) != null, a.getString(about)), prefs.aiKey(id) ?: !k.extra,
                    edit = { prompts(a.getString(name) + " · AI", R.string.prompt_keys, listOf(Triple(id, null, k.about))) { refreshModules() } }) { prefs.setAiKey(id, it) })
            }
        }
        t.addView(Rows.check(a, a.getString(R.string.prefetch_t), a.getString(R.string.prefetch_d), prefs.aiPrefetch) { prefs.aiPrefetch = it }.apply {
            (layoutParams as LinearLayout.LayoutParams).topMargin = Ink.dp(a, 22) })
    }

    /**
     * A prompt editor: [fields] (setting id, a label over it or none, the original text), [note] on top; Reset puts the
     * originals back; the original text is stored as "not edited", so a later app version can improve it.
     */
    private fun prompts(title: String, note: Int, fields: List<Triple<String, String?, String>>, done: () -> Unit) {
        val box = Ink.vertical(a).apply { val q = Ink.dp(a, 16); setPadding(q, q / 2, q, 0) }
        box.addView(Ink.text(a, a.getString(note), 13.5f))
        val edits = fields.map { (id, label, original) ->
            label?.let { box.addView(Ink.text(a, it, 15f, bold = true), Ink.lp().apply { topMargin = Ink.dp(a, 12) }) }
            multiline(a, prefs.prompt(id) ?: original).also { box.addView(it, Ink.lp().apply { topMargin = Ink.dp(a, if (label == null) 8 else 0) }) }
        }
        InkDialog(a).title(title).view(box)
            .button(R.string.cancel)
            .button(R.string.prompt_reset) { fields.forEach { prefs.setPrompt(it.first, null) }; done() }
            .button(R.string.save) {
                fields.zip(edits).forEach { (f, e) -> prefs.setPrompt(f.first, e.text.toString().trim().takeIf { it.isNotEmpty() && it != f.third }) }
                done()
            }.show()
    }

    private fun multiline(ctx: android.content.Context, text: String) = EditText(ctx).apply {
        setText(text); textSize = 15f; inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE; minLines = 2; maxLines = 10
    }

    // --- Models ---
    private fun chooseModel(done: () -> Unit) {
        val p = prefs.provider; a.toast(a.getString(R.string.loading_models))
        a.launch {
            val r = withContext(Dispatchers.IO) { runCatching { Ai.models(p, prefs.effectiveKey(p), prefs.url(p)) } }
            r.fold({ models -> pickModel(p, models, done) }, { a.toast(it.message ?: it.javaClass.simpleName) })
        }
    }

    /** Model list with a text filter, a maker filter, "free only" and sorting. */
    private fun pickModel(p: Provider, models: List<ModelInfo>, done: () -> Unit) {
        val box = Ink.vertical(a).apply { val q = Ink.dp(a, 12); setPadding(q, q, q, 0) }
        val filter = EditText(a).apply { hint = a.getString(R.string.filter); textSize = 16f; setSingleLine() }
        val makers = listOf(a.getString(R.string.all_publishers)) + models.map { it.publisher }.filter { it.isNotBlank() }
            .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        var maker = 0; var sort = 0
        val freeOnly = CheckBox(a).apply { text = a.getString(R.string.free_only); textSize = 16f; visibility = if (models.any { it.free }) View.VISIBLE else View.GONE }
        val list = ListView(a)
        var shown = models
        fun label(m: ModelInfo) = m.id + when {
            m.free -> "  · " + a.getString(R.string.free)
            m.price != null -> "  · $%.2f/$%.2f".format(java.util.Locale.US, m.price.first, m.price.second)
            else -> ""
        } + if (m.context > 0) "  · ${m.context / 1000}k" else ""
        fun apply() {
            val f = filter.text.toString().trim().lowercase()
            shown = models.filter { m ->
                (!freeOnly.isChecked || m.free) && (maker == 0 || m.publisher == makers[maker]) &&
                    (f.isEmpty() || m.id.lowercase().contains(f) || m.name.lowercase().contains(f))
            }.let { l -> when (sort) { 1 -> l.sortedBy { it.price?.first ?: Double.MAX_VALUE }; 2 -> l.sortedByDescending { it.created }; else -> l.sortedBy { it.id } } }
            list.adapter = ArrayAdapter(a, android.R.layout.simple_list_item_1, shown.map(::label))
        }
        box.addView(filter)
        if (makers.size > 2) box.addView(Rows.spinner(a, makers, 0) { maker = it; apply() })
        val row = Ink.horizontal(a).apply { gravity = Gravity.CENTER_VERTICAL }
        row.addView(freeOnly, Ink.lp(0, weight = 1f))
        row.addView(Rows.spinner(a, listOf(a.getString(R.string.sort_name), a.getString(R.string.sort_price), a.getString(R.string.sort_new)), 0) { sort = it; apply() },
            Ink.lp(0, weight = 1f))
        box.addView(row)
        box.addView(list, Ink.lp(h = Ink.dp(a, 380)))
        apply()
        val dlg = InkDialog(a).title(a.getString(R.string.models_title, p.title, models.size)).view(box, scroll = false)
            .button(R.string.cancel).show()
        filter.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = apply()
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })
        freeOnly.setOnCheckedChangeListener { _, _ -> apply() }
        list.setOnItemClickListener { _, _, i, _ -> prefs.setModel(p, shown[i].id); dlg.dismiss(); done() }
    }

    companion object {
        /** An answer key's title and explanation (AI → Modules). */
        private val KEYS = mapOf("grammar" to (R.string.ak_grammar to R.string.ak_grammar_d), "words" to (R.string.ak_words to R.string.ak_words_d),
            "related" to (R.string.ak_related to R.string.ak_related_d), "synonyms" to (R.string.ak_synonyms to R.string.ak_synonyms_d),
            "explanation" to (R.string.ak_explanation to R.string.ak_explanation_d), "examples" to (R.string.ak_examples to R.string.ak_examples_d),
            "idioms" to (R.string.ak_idioms to R.string.ak_idioms_d), "origin" to (R.string.ak_origin to R.string.ak_origin_d))
    }
}
