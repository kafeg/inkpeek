package io.github.kafeg.inkpeek

import android.content.Context
import android.os.Environment
import io.github.kafeg.inkpeek.ai.Action
import io.github.kafeg.inkpeek.ai.Provider
import io.github.kafeg.inkpeek.stardict.Library
import org.json.JSONArray
import java.io.File

/** All settings in one SharedPreferences file. */
class Prefs(private val ctx: Context) {
    private val sp = ctx.getSharedPreferences("inkpeek", Context.MODE_PRIVATE)

    var fontSp: Int get() = sp.getInt("font_sp", 20); set(v) = sp.edit().putInt("font_sp", v).apply()
    var dim: Boolean get() = sp.getBoolean("dim", true); set(v) = sp.edit().putBoolean("dim", v).apply()
    /** center | top | bottom | reader */
    var position: String get() = sp.getString("position", "center")!!; set(v) = sp.edit().putString("position", v).apply()
    var phraseAutoAi: Boolean get() = sp.getBoolean("phrase_auto_ai", true); set(v) = sp.edit().putBoolean("phrase_auto_ai", v).apply()
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

    /** Ids of the AI tabs shown in the card. */
    var actionIds: Set<String>
        get() = sp.getStringSet("actions_on", null)?.toSet() ?: Action.ALL.filter { it.onByDefault }.map { it.id }.toSet()
        set(v) = sp.edit().putStringSet("actions_on", v).apply()
    val actions: List<Action> get() = actionIds.let { on -> Action.ALL.filter { it.id in on } }

    /** The app folder on shared storage: dictionaries and key files (needs all-files access). */
    val home: File get() = File(Environment.getExternalStorageDirectory(), "InkPeek")
    val dictsDir: File get() = File(home, "dicts").apply { mkdirs() }
    val keysDir: File get() = File(home, "keys").apply { mkdirs() }

    fun library(): Library = lib ?: Library(listOf(dictsDir), File(ctx.filesDir, "index")).also { lib = it }

    companion object { private var lib: Library? = null }
}
