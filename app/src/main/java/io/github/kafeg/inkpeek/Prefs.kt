package io.github.kafeg.inkpeek

import android.content.Context
import android.os.Environment
import io.github.kafeg.inkpeek.ai.Provider
import io.github.kafeg.inkpeek.stardict.Library
import org.json.JSONArray
import java.io.File

/** All settings in one SharedPreferences file. */
class Prefs(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("inkpeek", Context.MODE_PRIVATE)

    /** "" = system, else a language code with resources (en, ru, de). */
    var uiLang: String get() = sp.getString("ui_lang", "")!!; set(v) = sp.edit().putString("ui_lang", v).apply()
    var phraseAutoAi: Boolean get() = sp.getBoolean("phrase_auto_ai", false); set(v) = sp.edit().putBoolean("phrase_auto_ai", v).apply()
    var aiOn: Boolean get() = sp.getBoolean("ai_on", true); set(v) = sp.edit().putBoolean("ai_on", v).apply()
    /** Default: the system language, by its English name ("Russian", "German"). */
    var answerLang: String
        get() = sp.getString("answer_lang", null) ?: java.util.Locale.getDefault().getDisplayLanguage(java.util.Locale.ENGLISH)
        set(v) = sp.edit().putString("answer_lang", v).apply()
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

    var order: List<String>
        get() = JSONArray(sp.getString("dict_order", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } }
        set(v) = sp.edit().putString("dict_order", JSONArray(v).toString()).apply()
    var disabled: Set<String>
        get() = sp.getStringSet("dict_disabled", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("dict_disabled", v).apply()

    /** Which "no reasoning" request variant a (host, model) accepts, see Ai. */
    fun variant(key: String): Int? = sp.getInt("rv_$key", -1).takeIf { it >= 0 }
    fun setVariant(key: String, v: Int) { if (variant(key) != v) sp.edit().putInt("rv_$key", v).apply() }

    /** The app folder on shared storage: dictionaries and key files (needs all-files access). */
    val home: File get() = File(Environment.getExternalStorageDirectory(), "InkPeek")
    val dictsDir: File get() = File(home, "dicts").apply { mkdirs() }
    val keysDir: File get() = File(home, "keys").apply { mkdirs() }
    /** Offline translation models, one folder per direction (deen, enru). */
    val modelsDir: File get() = File(home, "models").apply { mkdirs() }
    /** Translate tab: "ai" — AI when it can answer, else offline; "offline" — always offline. */
    var translateMode: String get() = sp.getString("translate_mode", "ai")!!; set(v) = sp.edit().putString("translate_mode", v).apply()
    /** The answer language as an ISO code ("ru"). */
    val answerCode: String get() = answerLang.let { n -> java.util.Locale.getAvailableLocales()
        .firstOrNull { it.getDisplayLanguage(java.util.Locale.ENGLISH) == n }?.language.orEmpty() }

    /** Language packs from our releases: dictionaries, grammar, models, langid. */
    val packs: io.github.kafeg.inkpeek.packs.Packs get() = io.github.kafeg.inkpeek.packs.Packs(File(home, "packs"), modelsDir)

    /** Dictionaries for the card: the packs' first, then the user's own. */
    fun library(): Library = lib ?: Library(listOf(packs.dicts, dictsDir), File(ctx.filesDir, "index")).also { lib = it }

    /** The packs' grammar (header, language of a text); its own index folder — a Library cleans up what it does not know. */
    fun grammar(): Library = gram ?: Library(listOf(packs.grammar), File(ctx.filesDir, "index-grammar")).also { gram = it }

    /** After installing or removing packs. */
    fun reload() { lib = null; gram = null }

    companion object { private var lib: Library? = null; private var gram: Library? = null }
}
