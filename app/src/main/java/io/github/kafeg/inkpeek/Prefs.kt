package io.github.kafeg.inkpeek

import android.content.Context
import android.os.Environment
import io.github.kafeg.inkpeek.ai.Provider
import io.github.kafeg.inkpeek.stardict.Library
import io.github.kafeg.inkpeek.stardict.StarDict
import io.github.kafeg.inkpeek.stardict.answer
import io.github.kafeg.inkpeek.stardict.book
import java.io.File

/** All settings in one SharedPreferences file. */
class Prefs(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("inkpeek", Context.MODE_PRIVATE)

    init {
        // The old "AI icons" switch turned off: the AI modules start off (no part ticked), as the icons were hidden.
        if (sp.contains("card_ai")) {
            if (!sp.getBoolean("card_ai", true)) sp.edit().apply {
                listOf(io.github.kafeg.inkpeek.ai.Ask.ANALYSIS, io.github.kafeg.inkpeek.ai.Ask.CONTEXT).forEach { a -> a.keys.forEach { putBoolean("ai_key_${a.section}.${it.name}", false) } }
            }.apply()
            sp.edit().remove("card_ai").apply()
        }
    }

    /** "" = system, else a language code with resources (Prefs.ANSWERS). */
    var uiLang: String get() = sp.getString("ui_lang", "")!!; set(v) = sp.edit().putString("ui_lang", v).apply()
    /** What a phrase opens on: "offline" (translation), "ai" (translation), "dict" (word by word), "analysis", "context". */
    var phraseOpen: String get() = sp.getString("phrase_open", "offline")!!; set(v) = sp.edit().putString("phrase_open", v).apply()
    /** Opening one AI tab asks for the card's other AI tabs at once (they open without waiting). */
    var aiPrefetch: Boolean get() = sp.getBoolean("ai_prefetch", true); set(v) = sp.edit().putBoolean("ai_prefetch", v).apply()

    /** The card's look (Card → Appearance); the defaults are the 0.3 card. Height 0 = by its content. */
    var cardHeight: Int get() = sp.getInt("card_height", 80); set(v) = sp.edit().putInt("card_height", v).apply()
    var cardWidth: Int get() = sp.getInt("card_width", 92); set(v) = sp.edit().putInt("card_width", v).apply()
    /** "center", "top", "bottom" or "reader" (where the reader asks: ColorDict's EXTRA_GRAVITY / EXTRA_HEIGHT). */
    var cardPlace: String get() = sp.getString("card_place", "center")!!; set(v) = sp.edit().putString("card_place", v).apply()
    var cardText: Int get() = sp.getInt("card_text", 20); set(v) = sp.edit().putInt("card_text", v).apply()
    /** How much the page behind the card is dimmed, %; 0 = not at all (default: e-ink shows a dim as a grey layer). */
    var cardDim: Int get() = sp.getInt("card_dim", 0); set(v) = sp.edit().putInt("card_dim", v).apply()
    /** light | dark | system, for the whole app (the key is from when only the card had it). */
    var theme: String get() = sp.getString("card_theme", "light")!!; set(v) = sp.edit().putString("card_theme", v).apply()
    /** The card's font: "" (Android's) or a font file's path. */
    var cardFont: String get() = sp.getString("card_font", "")!!; set(v) = sp.edit().putString("card_font", v).apply()
    /** The card's content turns by whole pages (a swipe up or down) instead of scrolling. */
    var cardPaged: Boolean get() = sp.getBoolean("card_paged", false); set(v) = sp.edit().putBoolean("card_paged", v).apply()
    var cardBar: Boolean get() = sp.getBoolean("card_bar", true); set(v) = sp.edit().putBoolean("card_bar", v).apply()
    /** AI translation in the Translate tab (AI → Modules). */
    var aiTranslate: Boolean get() = sp.getBoolean("ai_translate", true); set(v) = sp.edit().putBoolean("ai_translate", v).apply()
    /** The card's ☆ and Notes icons; the history is kept either way. */
    var cardSave: Boolean get() = sp.getBoolean("card_save", true); set(v) = sp.edit().putBoolean("card_save", v).apply()
    var history: Boolean get() = sp.getBoolean("history", true); set(v) = sp.edit().putBoolean("history", v).apply()
    /** AI chosen (AI tab); never chosen yet — on only with a key or a server already there, so a fresh install starts without AI. */
    var aiOn: Boolean get() = if (sp.contains("ai_on")) sp.getBoolean("ai_on", false) else effectiveKey(provider).isNotBlank() || url(Provider.CUSTOM).isNotBlank()
        set(v) = sp.edit().putBoolean("ai_on", v).apply()
    /** "" = as the app's language, else one of [ANSWERS]: an English app with answers in German. */
    var answerPick: String get() = sp.getString("answer_code", "")!!.takeIf { it.isEmpty() || it in ANSWERS }.orEmpty()
        set(v) = sp.edit().putString("answer_code", v).apply() // not "answer_lang": v0.1 kept a language's name there
    /** The language of the answers (dictionaries, translations, AI): its own setting, else the app's language (setting or
     *  system), one of [ANSWERS]; English otherwise. */
    val answerCode: String get() = answerPick.ifBlank { uiLang.ifBlank { java.util.Locale.getDefault().language } }.let { if (it in ANSWERS) it else "en" }
    /** Its English name ("Russian"): what AI prompts and saved answers use. */
    val answerLang: String get() = java.util.Locale(answerCode).getDisplayLanguage(java.util.Locale.ENGLISH)
    var provider: Provider
        get() = runCatching { Provider.valueOf(sp.getString("provider", Provider.ANTHROPIC.name)!!) }.getOrDefault(Provider.ANTHROPIC)
        set(v) = sp.edit().putString("provider", v.name).apply()

    fun key(p: Provider): String = sp.getString("key_${p.name}", "")!!
    fun setKey(p: Provider, v: String) = sp.edit().putString("key_${p.name}", v.trim()).apply()
    fun model(p: Provider): String = sp.getString("model_${p.name}", p.defaultModel)!!
    fun setModel(p: Provider, v: String) = sp.edit().putString("model_${p.name}", v.trim()).apply()
    /** API address: fixed for the big providers, a setting for [Provider.CUSTOM]. */
    fun url(p: Provider): String = if (p == Provider.CUSTOM) sp.getString("url_${p.name}", "")!! else p.baseUrl
    fun setUrl(p: Provider, v: String) = sp.edit().putString("url_${p.name}", v.trim()).apply()

    /** Key typed in settings, else the key file in [keysDir]. */
    fun effectiveKey(p: Provider): String = key(p).ifBlank { read(keyPath(p)) }
    fun keyPath(p: Provider) = File(keysDir, "${p.name.lowercase()}.key")
    enum class KeyFrom { TYPED, FILE, NONE }
    /** Where the key comes from, for the settings screen (never the key itself). */
    fun keySource(p: Provider): KeyFrom = when {
        key(p).isNotBlank() -> KeyFrom.TYPED
        read(keyPath(p)).isNotBlank() -> KeyFrom.FILE
        else -> KeyFrom.NONE
    }
    private fun read(f: File) = runCatching { f.readText().trim() }.getOrDefault("")

    /** An edited part of an AI prompt ("analysis.grammar", "system"); null — the original. */
    fun prompt(key: String): String? = sp.getString("prompt_$key", null)
    fun setPrompt(key: String, v: String?) = sp.edit().apply { if (v == null) remove("prompt_$key") else putString("prompt_$key", v) }.apply()

    /** Which "no reasoning" request variant a (host, model) accepts, see Ai. */
    fun variant(key: String): Int? = sp.getInt("rv_$key", -1).takeIf { it >= 0 }
    fun setVariant(key: String, v: Int) { if (variant(key) != v) sp.edit().putInt("rv_$key", v).apply() }

    /** The app folder on shared storage: dictionaries and key files (needs all-files access). */
    val home: File get() = File(Environment.getExternalStorageDirectory(), "InkPeek")
    val keysDir: File get() = File(home, "keys").apply { mkdirs() }
    /** Offline translation models, one folder per direction (deen, enru). */
    val modelsDir: File get() = File(home, "models").apply { mkdirs() }
    /** The ticked language pairs ("de-ru"): the card uses only these; null — every installed pair (until the first change). */
    var pairs: Set<String>? get() = sp.getStringSet("pairs", null)?.toSet(); set(v) = sp.edit().putStringSet("pairs", v).apply()

    /** Language packs from our releases: dictionaries, models, langid. */
    val packs: io.github.kafeg.inkpeek.packs.Packs get() = io.github.kafeg.inkpeek.packs.Packs(File(home, "packs"), modelsDir)

    /** What the card takes from the packs: words (articles, gloss), grammar (the header), offline translation; language id always. */
    var packDicts: Boolean get() = sp.getBoolean("pack_dicts", true); set(v) = sp.edit().putBoolean("pack_dicts", v).apply()
    var packGrammar: Boolean get() = sp.getBoolean("pack_grammar", true); set(v) = sp.edit().putBoolean("pack_grammar", v).apply()
    /** A part of an AI module ticked (AI → Modules); null: never changed (its default). */
    fun aiKey(k: String): Boolean? = if (sp.contains("ai_key_$k")) sp.getBoolean("ai_key_$k", true) else null
    fun setAiKey(k: String, on: Boolean) = sp.edit().putBoolean("ai_key_$k", on).apply()
    /** The rows under a pack's article (milestone 17): rel, syn, expr, forms on; origin off. */
    fun packSection(k: String) = sp.getBoolean("pack_sec_$k", k != "ety")
    fun setPackSection(k: String, on: Boolean) = sp.edit().putBoolean("pack_sec_$k", on).apply()

    /** Offline translation: "off", "fast" (Mozilla), "direct" (OPUS-MT), "llm"; "off" also for the old switch turned off. */
    var mtMethod: String get() = sp.getString("mt_method", if (sp.getBoolean("pack_mt", true)) "fast" else "off")!!
        set(v) = sp.edit().putString("mt_method", v).apply()
    /** Offline translation can work: a method on and models for it (Mozilla's, the fallback of every method, or the method's own). */
    fun offlineReady(): Boolean {
        if (mtMethod == "off") return false
        val have = packs.installed()
        if (have.keys.any { it.startsWith("model-") }) return true
        val kind = io.github.kafeg.inkpeek.packs.Packs.kind(mtMethod) ?: return false
        return packs.cachedList().orEmpty().any { it.kind == kind && it.id in have }
    }
    /** An AI module (AI → Modules) can ask: AI on and a part of it ticked. */
    fun aiModule(ask: io.github.kafeg.inkpeek.ai.Ask) = aiOn && ask.keys.any { aiKey("${ask.section}.${it.name}") ?: !it.extra }
    /** Mozilla's English in between shown under a translation through English. */
    var mtEnglish: Boolean get() = sp.getBoolean("mt_english", false); set(v) = sp.edit().putBoolean("mt_english", v).apply()
    /** A method's measured time per phrase on this device, ms (0: not measured). */
    fun mtSpeed(method: String) = sp.getInt("mt_speed_$method", 0)
    fun setMtSpeed(method: String, ms: Int) = sp.edit().putInt("mt_speed_$method", ms).apply()
    /** Own dictionaries switched off, by id. */
    var dictsOff: Set<String> get() = sp.getStringSet("dicts_off", emptySet())!!.toSet(); set(v) = sp.edit().putStringSet("dicts_off", v).apply()
    /** Other dictionaries downloaded from the sources: "address\tname\tname…"; the download page marks those still there. */
    var got: Set<String> get() = sp.getStringSet("got", emptySet())!!.toSet(); set(v) = sp.edit().putStringSet("got", v).apply()
    /** The daily update check (About); when it last ran; the latest release seen then (JSON, see Updates). */
    var updateCheck: Boolean get() = sp.getBoolean("update_check", true); set(v) = sp.edit().putBoolean("update_check", v).apply()
    var updateAt: Long get() = sp.getLong("update_at", 0); set(v) = sp.edit().putLong("update_at", v).apply()
    var updateSeen: String get() = sp.getString("update_seen", "")!!; set(v) = sp.edit().putString("update_seen", v).apply()
    /** Download sources switched off (Catalog.SOURCES ids). */
    var sourcesOff: Set<String> get() = sp.getStringSet("sources_off", emptySet())!!.toSet(); set(v) = sp.edit().putStringSet("sources_off", v).apply()
    /** The other dictionaries' one folder: read from it, downloads and imports go into it (can be KOReader's). */
    var dictsDir: File get() = sp.getString("dicts_dir", null)?.let(::File) ?: File(home, "dicts")
        set(v) = sp.edit().putString("dicts_dir", v.path).apply()

    /** The order of the card's dictionaries (ids), packs and others together (Card → Article → Dictionary order). */
    var order: List<String> get() = sp.getString("order", "")!!.split('\n').filter { it.isNotEmpty() }
        set(v) = sp.edit().putString("order", v.joinToString("\n")).apply()
    /** The card's Dictionary tab: full articles with ◀ ▶ between dictionaries (true) or short lines (false). */
    var cardFull: Boolean get() = sp.getBoolean("card_full", false); set(v) = sp.edit().putBoolean("card_full", v).apply()

    /** The packs' dictionaries and the own ones (imported, downloaded, folders). */
    fun library(): Library = lib ?: synchronized(Prefs::class) { lib ?: sp.let { p ->
        // Shared by every screen: it reads the switches from the preferences, not from this object.
        Library(packs.dicts, listOf(dictsDir), File(ctx.filesDir, "index"), { d, pack ->
            if (pack) p.getBoolean("pack_dicts", true) && ticked(p, d)
            else d.id !in p.getStringSet("dicts_off", emptySet())!!
        }) { p.getString("order", "")!!.split('\n').filter { it.isNotEmpty() } }
    }.also { lib = it } }

    /** The ticked pairs' pack dictionaries, even when the card does not use them: they tell the text's language. */
    fun packDictionaries(): List<StarDict> = library().let { l -> l.found().filter { l.isPack(it) && ticked(sp, it) } }


    /** After installing or removing packs, changing the dictionaries' folder. */
    fun reload() { lib = null }

    companion object {
        @Volatile private var lib: Library? = null
        // Here, not in the instance: the process-wide library must not hold the screen that made it.
        private fun ticked(p: android.content.SharedPreferences, d: StarDict) = p.getStringSet("pairs", null)?.contains("${d.book}-${d.answer}") ?: true
        /** App languages = the answer languages of the packs. */
        val ANSWERS = listOf("en", "ru", "de", "fr", "es", "it", "pt", "pl")
    }
}
