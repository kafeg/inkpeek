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

    /** "" = system, else a language code with resources (en, ru, de). */
    var uiLang: String get() = sp.getString("ui_lang", "")!!; set(v) = sp.edit().putString("ui_lang", v).apply()
    var phraseAutoAi: Boolean get() = sp.getBoolean("phrase_auto_ai", true); set(v) = sp.edit().putBoolean("phrase_auto_ai", v).apply()

    /** The card's look (Card → Look); the defaults are the 0.3 card. Height 0 = by its content. */
    var cardHeight: Int get() = sp.getInt("card_height", 80); set(v) = sp.edit().putInt("card_height", v).apply()
    var cardWidth: Int get() = sp.getInt("card_width", 92); set(v) = sp.edit().putInt("card_width", v).apply()
    /** "center", "top", "bottom" or "reader" (where the reader asks: ColorDict's EXTRA_GRAVITY / EXTRA_HEIGHT). */
    var cardPlace: String get() = sp.getString("card_place", "center")!!; set(v) = sp.edit().putString("card_place", v).apply()
    var cardText: Int get() = sp.getInt("card_text", 20); set(v) = sp.edit().putInt("card_text", v).apply()
    /** "light", "dark" or "system" (Android's night mode). */
    var cardTheme: String get() = sp.getString("card_theme", "light")!!; set(v) = sp.edit().putString("card_theme", v).apply()
    var cardBar: Boolean get() = sp.getBoolean("card_bar", true); set(v) = sp.edit().putBoolean("card_bar", v).apply()
    /** The card's ☆ and My words icons; the history is kept either way. */
    var cardSave: Boolean get() = sp.getBoolean("card_save", true); set(v) = sp.edit().putBoolean("card_save", v).apply()
    var aiOn: Boolean get() = sp.getBoolean("ai_on", true); set(v) = sp.edit().putBoolean("ai_on", v).apply()
    /** The language of the answers is the app's language (setting or system), one of [ANSWERS]; English otherwise. */
    val answerCode: String get() = uiLang.ifBlank { java.util.Locale.getDefault().language }.let { if (it in ANSWERS) it else "en" }
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
    /** Where the key comes from, for the settings screen (never the key itself). */
    fun keySource(p: Provider): String = when {
        key(p).isNotBlank() -> "typed in settings"
        read(keyPath(p)).isNotBlank() -> keyPath(p).path
        else -> "none"
    }
    private fun read(f: File) = runCatching { f.readText().trim() }.getOrDefault("")

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
    var packMt: Boolean get() = sp.getBoolean("pack_mt", true); set(v) = sp.edit().putBoolean("pack_mt", v).apply()
    /** Own dictionaries switched off, by id. */
    var dictsOff: Set<String> get() = sp.getStringSet("dicts_off", emptySet())!!.toSet(); set(v) = sp.edit().putStringSet("dicts_off", v).apply()
    /** Folders whose StarDict dictionaries are used where they are (KOReader's data/dict). */
    var folders: Set<String> get() = sp.getStringSet("dict_folders", emptySet())!!.toSet(); set(v) = sp.edit().putStringSet("dict_folders", v).apply()
    /** Download sources switched off (Catalog.SOURCES ids). */
    var sourcesOff: Set<String> get() = sp.getStringSet("sources_off", emptySet())!!.toSet(); set(v) = sp.edit().putStringSet("sources_off", v).apply()
    /** Imported and downloaded dictionaries. */
    val dictsDir: File get() = File(home, "dicts")

    /** The packs' dictionaries and the own ones (imported, downloaded, folders). */
    fun library(): Library = lib ?: sp.let { p ->
        // Shared by every screen: it reads the switches from the preferences, not from this object.
        Library(packs.dicts, listOf(dictsDir) + folders.map(::File), File(ctx.filesDir, "index")) { d, pack ->
            if (pack) p.getBoolean("pack_dicts", true) && ticked(p, d)
            else d.id !in p.getStringSet("dicts_off", emptySet())!!
        }
    }.also { lib = it }

    /** The ticked pairs' pack dictionaries, even when the card does not use them: they tell the text's language. */
    fun packDictionaries(): List<StarDict> = library().let { l -> l.found().filter { l.isPack(it) && ticked(sp, it) } }

    private fun ticked(p: android.content.SharedPreferences, d: StarDict) = p.getStringSet("pairs", null)?.contains("${d.book}-${d.answer}") ?: true

    /** After installing or removing packs, adding or removing a folder. */
    fun reload() { lib = null }

    companion object {
        private var lib: Library? = null
        /** App languages = the answer languages of the packs. */
        val ANSWERS = listOf("en", "ru", "de", "fr", "es", "it", "pt", "pl")
    }
}
