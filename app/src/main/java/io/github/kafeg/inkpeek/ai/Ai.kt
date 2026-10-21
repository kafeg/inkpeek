package io.github.kafeg.inkpeek.ai

import android.util.Log
import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.errors.BadRequestException
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.BetaJsonOutputFormat
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaThinkingConfigDisabled
import com.anthropic.models.beta.messages.MessageCreateParams
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class Provider(val title: String, val baseUrl: String, val defaultModel: String) {
    ANTHROPIC("Claude (Anthropic)", "https://api.anthropic.com", "claude-sonnet-5-5"),
    OPENAI("OpenAI", "https://api.openai.com/v1", ""),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "openrouter/free"),
    /** Any server with the OpenAI API: LM Studio, Ollama, llama.cpp, vLLM, Groq, DeepSeek…; the address is a setting. */
    CUSTOM("OpenAI-compatible server", "", ""),
}

/** Ready addresses for [Provider.CUSTOM]; for a local server only the IP needs changing. */
data class Preset(val name: String, val url: String) {
    companion object {
        val ALL = listOf(
            Preset("LM Studio", "http://192.168.0.100:1234/v1"),
            Preset("Ollama", "http://192.168.0.100:11434/v1"),
            Preset("llama.cpp server", "http://192.168.0.100:8080/v1"),
            Preset("vLLM", "http://192.168.0.100:8000/v1"),
            Preset("Groq", "https://api.groq.com/openai/v1"),
            Preset("DeepSeek", "https://api.deepseek.com/v1"),
            Preset("Qwen (Alibaba Cloud)", "https://dashscope-intl.aliyuncs.com/compatible-mode/v1"),
            Preset("Mistral", "https://api.mistral.ai/v1"),
            Preset("Google Gemini", "https://generativelanguage.googleapis.com/v1beta/openai"),
        )
    }
}

/** What was selected: one word, a short phrase, or a passage (more than 8 words or several sentences). */
enum class Kind {
    WORD, PHRASE, PASSAGE;
    companion object {
        /** Scripts written without spaces between words: there a "word" is counted as about two characters. */
        private val NO_SPACES = Regex("[\\p{IsHan}\\p{IsHiragana}\\p{IsKatakana}\\p{IsThai}\\p{IsLao}\\p{IsKhmer}\\p{IsMyanmar}]")

        fun words(text: String): Int = text.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.sumOf { w ->
            val n = NO_SPACES.findAll(w).count()
            if (n > 3) (n + 1) / 2 + (w.length - n).coerceAtMost(1) else 1
        }

        fun of(text: String): Kind {
            val words = words(text)
            val sentences = Regex("[.!?…]\\s+\\p{Lu}|[。！？]\\s*\\S").findAll(text.trim()).count() + 1
            return when { words == 1 -> WORD; words <= 8 && sentences == 1 -> PHRASE; else -> PASSAGE }
        }
    }
}

/** An AI tab of the card; [id] keys its label and description in resources. Translate is a core tab with an AI mode. */
data class Action(val id: String) {
    companion object { val ALL = listOf(Action("analysis"), Action("context")) }
}

/** One key of an AI answer: its [name] and JSON type are fixed, [about] (what to write there) can be edited. */
data class Key(val name: String, val type: String, val about: String)

/**
 * The AI requests, each answered as one JSON object: [QUICK] for Translate (head facts + translation), [ANALYSIS] and
 * [CONTEXT] for their tabs; opening any AI tab sends all three at once. Placeholders: {text}, {lang}. The prompt is
 * [intro], one "- key: about" line per key, [tail]; settings may replace the abouts (AI → Prompts).
 */
enum class Ask(val intro: String, val keys: List<Key>, val tail: String, val maxTokens: Long) {
    QUICK("Answer with one JSON object, these keys only:", listOf(
        Key("base", "s", "the dictionary form in the text's own language (Häusern → Haus, went → go, ging → gehen); \"\" for a phrase or passage."),
        Key("article", "s", "the article of the base that shows its gender where the language has one (German der/die/das, " +
            "French le/la, Spanish el/la…); \"\" otherwise or for non-nouns."),
        Key("plural", "s", "a noun's plural in the text's language, with the article for German (die Häuser); else \"\"."),
        Key("pos", "s", "part of speech in {lang}, short; for a noun add the gender of the ORIGINAL word, not of its translation."),
        Key("forms", "s", "a verb's principal parts in the text's language — German: infinitive · 3rd person singular present · " +
            "Präteritum · Perfekt with its auxiliary (\"gehen · geht · ging · ist gegangen\"); English: \"go · went · gone\"; " +
            "an adjective with irregular comparison: \"gut · besser · am besten\"; else \"\"."),
        Key("irregular", "b", "true for an irregular or strong verb (or irregular comparison), else false."),
        Key("form_of", "s", "when the selected word is an inflected form, which form, in {lang} (plural, dative; past tense…); else \"\"."),
        Key("meanings", "a", "1–3 main meanings in {lang}, a few words each; [] for a phrase or passage."),
        Key("translation", "s", "a phrase or passage: a natural translation into {lang} that keeps the sentence type (a question " +
            "stays a question); \"\" for a single word.")),
        "Grammar, examples and comments do not belong here: other tabs give them.", 1024),
    // The two AI tabs, one request each; the parts as in the separate tabs before (v0.2), the app adds the headings.
    ANALYSIS("Answer with one JSON object, these keys only; text in {lang} with light Markdown " +
        "(**bold**, *italic*, \"- \" lists), no headings: the app adds them.", listOf(
        Key("grammar", "s", "at most 7 short lines, the words you talk about in **bold**. A single word: which form of which " +
            "**base form** it is, how it is built, and the forms a learner of this language needs to use it correctly. A " +
            "phrase: how it is built and why — the constructions that matter in this language (word order, agreement, which " +
            "word requires which form, tense, aspect or mood) and the rule behind them. A passage: every construction that " +
            "matters, up to 10 lines."),
        Key("words", "a", "a phrase or a passage: the words a learner may not know, \"**base form** — short meaning\" each (at most " +
            "20 for a passage), very common words skipped; a single word: its **base form**, 2–3 synonyms and related words, " +
            "each \"**word** — short meaning\".")), "", 3000),
    CONTEXT("Answer with one JSON object, these keys only; text in {lang} with light Markdown " +
        "(**bold**, *italic*), no headings: the app adds them.", listOf(
        Key("explanation", "s", "what it means here, register, idioms and nuances; a passage: what happens and what is implied, " +
            "briefly."),
        Key("examples", "e", "a word or phrase: 3 short everyday sentences in the text's own language with it in **bold**, each " +
            "with its translation into {lang}; [] for a passage.")), "", 3000);

    /** The prompt with the edited abouts ([edited]: "analysis.grammar" → text, null — the original). */
    fun prompt(edited: (String) -> String? = { null }): String = "Selected text: «{text}». $intro\n" +
        keys.joinToString("\n") { k -> "- ${k.name}: " + (edited("$section.${k.name}") ?: k.about) } + if (tail.isEmpty()) "" else "\n$tail"

    /** The original prompt's: saved answers stay when a prompt is edited (the owner's choice), they change with a new app. */
    val version: Int get() = prompt().hashCode()
    val section: String get() = name.lowercase()

    /** JSON Schema for structured outputs: every key required, no others. */
    fun schema(): Map<String, Any> = mapOf("type" to "object", "additionalProperties" to false, "required" to keys.map { it.name },
        "properties" to keys.associate { k -> k.name to when (k.type) { "b" -> mapOf("type" to "boolean")
            "a" -> mapOf("type" to "array", "items" to mapOf("type" to "string"))
            "e" -> mapOf("type" to "array", "items" to mapOf("type" to "object", "additionalProperties" to false, "required" to listOf("text", "translation"),
                "properties" to mapOf("text" to mapOf("type" to "string"), "translation" to mapOf("type" to "string"))))
            else -> mapOf("type" to "string") } })
}

class AiError(msg: String) : Exception(msg)

/** A model from the provider's list; price in USD per million tokens (input, output) when known. */
data class ModelInfo(val id: String, val name: String = id, val free: Boolean = false, val price: Pair<Double, Double>? = null,
                     val publisher: String = id.substringBefore('/', ""), val created: Long = 0, val context: Int = 0)

object Ai {
    /** The general style of every answer; {lang} is the answer language. Editable (AI → Prompts) as "system". */
    const val SYSTEM = "You are a concise dictionary and language tutor inside an e-book reader on an " +
        "e-ink screen. Write every answer in {lang} only. If the text is already in {lang}, answer like an explanatory " +
        "dictionary of {lang} instead of translating. Formatting: only **bold** and *italic*, short lines, simple \"- \" " +
        "lists inside JSON string values. No headings, tables or code. Keep each part under 120 words unless it needs " +
        "more. You see only the selected text, " +
        "not the sentence around it: do not guess a context (a preposition, a speaker) that is not in the text."
    /** Edited prompt parts ("system", "analysis.grammar"); set by the app from its settings. */
    var edited: (String) -> String? = { null }
    private fun system(lang: String) = (edited("system") ?: SYSTEM).replace("{lang}", lang)
    // The answer comes in one piece, so the read timeout must cover the whole generation.
    private val http = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).readTimeout(45, TimeUnit.SECONDS).build()
    /** A model on a home computer can take a while, the first answer also loads the model. */
    private val slow = http.newBuilder().callTimeout(120, TimeUnit.SECONDS).readTimeout(120, TimeUnit.SECONDS).build()
    /** Server-side refusal fallback is offered for these Claude models. */
    private val FALLBACK = listOf("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5")
    /** Thinking cannot be switched off on these; effort low is the minimum. */
    private val ALWAYS_THINK = Regex("^claude-(opus-5-5|fable|mythos)")
    /** One client per key: a new one per request costs a TLS handshake. */
    private var claudeClient: Pair<String, AnthropicClient>? = null
    /** Remembers which "no reasoning" variant a (host, model) accepts; set by the app to persist it. */
    var recall: (String) -> Int? = { null }
    var remember: (String, Int) -> Unit = { _, _ -> }

    /** Blocking; call off the main thread. The answer is the model's JSON object. */
    fun ask(p: Provider, key: String, model: String, ask: Ask, text: String, lang: String, url: String = p.baseUrl): JSONObject {
        if (p == Provider.CUSTOM && url.isBlank()) throw AiError("No server address")
        if (key.isBlank() && p != Provider.CUSTOM) throw AiError("No API key for ${p.title}")
        if (model.isBlank()) throw AiError("No model selected for ${p.title}")
        val kind = Kind.of(text)
        val what = when (kind) { Kind.WORD -> "a single word"; Kind.PHRASE -> "a short phrase"
            Kind.PASSAGE -> "a passage of about ${Kind.words(text)} words" }
        val prompt = "The selection is $what.\n" + ask.prompt(edited).replace("{text}", text).replace("{lang}", lang)
        val t0 = System.currentTimeMillis()
        val raw = stripThinking(if (p == Provider.ANTHROPIC) claude(key, model, prompt, system(lang), ask) else openAi(p, url, key, model, prompt, system(lang), ask)).trim()
        Log.i("InkPeek", "ai ${p.name} $model ${ask.section} ${kind.name} ${System.currentTimeMillis() - t0} ms, ${raw.length} chars")
        return json(raw)
    }

    /** The JSON object in a reply, also when a model wraps it in a code fence or adds a sentence around it. */
    fun json(s: String): JSONObject {
        val a = s.indexOf('{'); val b = s.lastIndexOf('}')
        if (a < 0 || b <= a) throw AiError("The model did not answer in JSON: ${s.take(120)}")
        return JSONObject(s.substring(a, b + 1))
    }

    private fun client(key: String): AnthropicClient = synchronized(this) {
        claudeClient?.takeIf { it.first == key }?.second ?: AnthropicOkHttpClient.builder().apiKey(key).maxRetries(1).timeout(java.time.Duration.ofSeconds(45)).build()
            .also { c -> claudeClient?.second?.close(); claudeClient = key to c }
    }

    private fun claude(key: String, model: String, prompt: String, sys: String, ask: Ask): String {
        fun params(extras: Boolean) = MessageCreateParams.builder().model(model).maxTokens(ask.maxTokens).system(sys).addUserMessage(prompt).apply {
            if (!extras) return@apply
            // Least thinking the model allows: none, or "between_tools" (Sonnet 5.5), or only effort low (Opus 5.5).
            outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).format(BetaJsonOutputFormat.builder()
                .schema(BetaJsonOutputFormat.Schema.builder().putAllAdditionalProperties(ask.schema().mapValues { JsonValue.from(it.value) }).build())
                .build()).build())
            when {
                ALWAYS_THINK.containsMatchIn(model) -> {}
                model.startsWith("claude-sonnet-5-5") -> putAdditionalBodyProperty("thinking", JsonValue.from(mapOf("type" to "between_tools")))
                else -> thinking(BetaThinkingConfigDisabled.builder().build())
            }
            if (FALLBACK.any { model == it }) addBeta("server-side-fallback-2026-07-01").putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        }.build()
        val c = client(key); val memo = "claude-json|$model"
        // Older models reject effort or thinking: ask once more without them and remember that.
        val msg = if (recall(memo) == 1) c.beta().messages().create(params(false)) else try { c.beta().messages().create(params(true)) }
            catch (e: BadRequestException) {
                Log.i("InkPeek", "$model: 400 with effort / thinking / schema, plain from now on: ${e.message?.take(200)}")
                c.beta().messages().create(params(false)).also { remember(memo, 1) }
            }
        if (msg.stopReason().map { it.toString() }.orElse("") == "refusal") throw AiError("The model declined this request")
        return msg.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n")
    }

    private fun openAi(p: Provider, url: String, key: String, model: String, prompt: String, sys: String, ask: Ask): String {
        val body = JSONObject().put("model", model).put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", sys))
            .put(JSONObject().put("role", "user").put("content", prompt)))
        fun call(b: JSONObject): Pair<Int, String> {
            val req = Request.Builder().url(url.trimEnd('/') + "/chat/completions").apply { if (key.isNotBlank()) header("Authorization", "Bearer $key") }
                .header("X-Title", "InkPeek").post(b.toString().toRequestBody("application/json".toMediaType())).build()
            return (if (p == Provider.CUSTOM) slow else http).newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
        }
        // No reasoning pass: try the host's "off" field, then a weaker one, then none; remember what worked.
        // JSON mode first; a server without it gets the request again without, the prompt still asks for JSON.
        val variants = noReasoning(url) + JSONObject()
        val memo = "${url.substringAfter("://").substringBefore('/')}|$model"; val fmtMemo = "json|$memo"
        var json = recall(fmtMemo) != 1
        fun attempt(): Pair<Int, String> {
            val b = if (json) withFields(body, JSONObject().put("response_format", JSONObject().put("type", "json_object"))) else body
            var i = (recall(memo) ?: 0).coerceIn(0, variants.size - 1)
            var r = call(withFields(b, variants[i]))
            while ((r.first == 400 || r.first == 422) && i < variants.size - 1) { i++; r = call(withFields(b, variants[i])) }
            if (r.first in 200..299) remember(memo, i)
            return r
        }
        var (code, s) = attempt()
        if ((code == 400 || code == 422) && json) { json = false; attempt().let { code = it.first; s = it.second }; if (code in 200..299) remember(fmtMemo, 1) }
        if (code !in 200..299) throw AiError("${p.title}: HTTP $code ${errorText(s)}")
        val choices = JSONObject(s).optJSONArray("choices") ?: throw AiError("${p.title}: ${errorText(s)}")
        return choices.getJSONObject(0).getJSONObject("message").optString("content")
    }

    private fun withFields(body: JSONObject, extra: JSONObject) = JSONObject(body.toString()).apply { extra.keys().forEach { put(it, extra.get(it)) } }

    /** Fields that switch reasoning off on this server, strongest first (each provider names it differently). */
    private fun noReasoning(url: String): List<JSONObject> {
        fun o(vararg kv: Pair<String, Any>) = JSONObject().apply { kv.forEach { put(it.first, it.second) } }
        val host = url.substringAfter("://").substringBefore('/')
        return when {
            "openrouter.ai" in host -> listOf(o("reasoning" to o("effort" to "none")))
            "api.openai.com" in host -> listOf(o("reasoning_effort" to "none"), o("reasoning_effort" to "low"))
            "generativelanguage.googleapis.com" in host -> listOf(o("reasoning_effort" to "none"), o("reasoning_effort" to "minimal"))
            "deepseek.com" in host -> listOf(o("thinking" to o("type" to "disabled")))
            "dashscope" in host -> listOf(o("enable_thinking" to false))
            "groq.com" in host -> listOf(o("reasoning_effort" to "none"), o("reasoning_effort" to "low", "include_reasoning" to false))
            else -> listOf(o("reasoning_effort" to "none")) // Mistral, Ollama, llama.cpp, vLLM, LM Studio
        }
    }

    private val NOT_CHAT = Regex("embed|whisper|tts|transcri|moderation|guard|safety|dall-e|image|audio|realtime|search|:batch$", RegexOption.IGNORE_CASE)

    /** Chat models the provider offers (OpenRouter tells names, prices and free variants). */
    fun models(p: Provider, key: String, url: String = p.baseUrl): List<ModelInfo> {
        if (p == Provider.CUSTOM && url.isBlank()) throw AiError("No server address")
        if (key.isBlank() && p != Provider.OPENROUTER && p != Provider.CUSTOM) throw AiError("No API key for ${p.title}")
        if (p == Provider.ANTHROPIC) {
            val client = AnthropicOkHttpClient.builder().apiKey(key).build()
            return try { client.models().list().autoPager().map { ModelInfo(it.id(), it.displayName()) }.toList() } finally { client.close() }
        }
        val req = Request.Builder().url(url.trimEnd('/') + "/models").apply { if (key.isNotBlank()) header("Authorization", "Bearer $key") }.build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw AiError("${p.title}: HTTP ${r.code} ${errorText(s)}")
            val a = JSONObject(s).getJSONArray("data")
            return (0 until a.length()).map { a.getJSONObject(it) }.filter { m ->
                // text-only chat models: no embeddings, speech or image generators
                val out = m.optJSONObject("architecture")?.optJSONArray("output_modalities")
                !NOT_CHAT.containsMatchIn(m.getString("id")) && (out == null || (out.length() == 1 && out.getString(0) == "text"))
            }.map { m ->
                val pr = m.optJSONObject("pricing")
                val inP = pr?.optString("prompt")?.toDoubleOrNull()?.times(1e6); val outP = pr?.optString("completion")?.toDoubleOrNull()?.times(1e6)
                val id = m.getString("id")
                ModelInfo(id, m.optString("name", id), id.endsWith(":free") || (inP == 0.0 && outP == 0.0),
                    if (inP != null && outP != null) inP to outP else null,
                    id.substringBefore('/', m.optString("owned_by", "")), m.optLong("created"), m.optInt("context_length"))
            }.sortedBy { it.id }
        }
    }

    private fun errorText(s: String) = runCatching { JSONObject(s).getJSONObject("error").optString("message") }.getOrDefault(s.take(200))

    /** Removes <think>…</think> blocks some reasoning models put inline. */
    fun stripThinking(s: String) = s.replace(Regex("(?s)<think>.*?</think>"), "")
}
