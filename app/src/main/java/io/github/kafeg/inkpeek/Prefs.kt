package io.github.kafeg.inkpeek

import android.content.Context
import android.os.Environment
import io.github.kafeg.inkpeek.ai.Action
import io.github.kafeg.inkpeek.ai.Provider
import io.github.kafeg.inkpeek.stardict.Library
import org.json.JSONArray
import org.json.JSONObject
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
    var answerLang: String get() = sp.getString("answer_lang", "Russian")!!; set(v) = sp.edit().putString("answer_lang", v).apply()
    var provider: Provider
        get() = runCatching { Provider.valueOf(sp.getString("provider", Provider.ANTHROPIC.name)!!) }.getOrDefault(Provider.ANTHROPIC)
        set(v) = sp.edit().putString("provider", v.name).apply()
    var keyFile: String
        get() = sp.getString("key_file", "/sdcard/koreader/settings/anthropic.key")!!
        set(v) = sp.edit().putString("key_file", v).apply()

    fun key(p: Provider): String = sp.getString("key_${p.name}", "")!!
    fun setKey(p: Provider, v: String) = sp.edit().putString("key_${p.name}", v.trim()).apply()
    fun model(p: Provider): String = sp.getString("model_${p.name}", p.defaultModel)!!
    fun setModel(p: Provider, v: String) = sp.edit().putString("model_${p.name}", v.trim()).apply()

    /** Key from settings, else (Anthropic) from [keyFile]. */
    fun effectiveKey(p: Provider): String = key(p).ifBlank {
        if (p == Provider.ANTHROPIC) runCatching { File(keyFile).readText().trim() }.getOrDefault("") else ""
    }

    var order: List<String>
        get() = JSONArray(sp.getString("dict_order", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } }
        set(v) = sp.edit().putString("dict_order", JSONArray(v).toString()).apply()
    var disabled: Set<String>
        get() = sp.getStringSet("dict_disabled", emptySet())!!.toSet()
        set(v) = sp.edit().putStringSet("dict_disabled", v).apply()

    var actions: List<Action>
        get() = sp.getString("actions", null)?.let { s ->
            JSONArray(s).let { a -> (0 until a.length()).map { a.getJSONObject(it).let { o -> Action(o.getString("name"), o.getString("prompt")) } } }
        } ?: Action.DEFAULTS
        set(v) = sp.edit().putString("actions", JSONArray(v.map { JSONObject().put("name", it.name).put("prompt", it.prompt) }).toString()).apply()

    /** The one dictionary folder (shared with ColorDict); needs all-files access. */
    val dictsDir: File get() = File(Environment.getExternalStorageDirectory(), "dictdata").apply { mkdirs() }

    fun library(): Library = lib ?: Library(listOf(dictsDir), File(ctx.filesDir, "index")).also { lib = it }

    companion object { private var lib: Library? = null }
}
