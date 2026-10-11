package io.github.kafeg.inkpeek.ai

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.MessageCreateParams
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit

enum class Provider(val title: String, val baseUrl: String, val defaultModel: String) {
    ANTHROPIC("Claude (Anthropic)", "https://api.anthropic.com", "claude-opus-5-5"),
    OPENAI("OpenAI", "https://api.openai.com/v1", ""),
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", "openrouter/free"),
}

/** What was selected: one word, a short phrase, or a passage (more than 8 words or several sentences). */
enum class Kind {
    WORD, PHRASE, PASSAGE;
    companion object {
        fun of(text: String): Kind {
            val words = text.trim().split(Regex("\\s+")).size
            val sentences = Regex("[.!?…]\\s+\\p{Lu}").findAll(text).count() + 1
            return when { words == 1 -> WORD; words <= 8 && sentences == 1 -> PHRASE; else -> PASSAGE }
        }
    }
}

/**
 * An AI tab of the card. [id] keys its label and description in resources; [prompt] uses {text}, {lang} and is
 * told the [Kind]; [kinds] — selections the tab makes sense for (no "examples" for a whole passage).
 */
data class Action(val id: String, val prompt: String, val onByDefault: Boolean = true, val kinds: Set<Kind> = Kind.entries.toSet()) {
    companion object {
        val ALL = listOf(
            Action("translate", "Translate into {lang}: «{text}».\n" +
                "A single word: first line **base form** and part of speech (for nouns: gender and plural), then the " +
                "1–3 main meanings in *italics*; say so if the word is an inflected form.\n" +
                "A phrase or a passage: only a natural translation, nothing else."),
            Action("grammar", "Explain in {lang} how «{text}» is built and why, at most 7 short lines: word order (where " +
                "the verb stands and why), which word requires which case or form, tense and mood, and the rule behind " +
                "it. A passage: every construction that matters, up to 10 short lines. Put the words you talk about in " +
                "**bold**. No headings."),
            Action("words", "Vocabulary of «{text}», in {lang}.\nA phrase or a passage: list the words a learner may not " +
                "know, one per line: **base form** — short meaning (at most 20 for a passage). Skip very common words.\n" +
                "A single word: **base form**, 2–3 synonyms, related words, each with a short meaning."),
            Action("examples", "3 short everyday sentences with «{text}» in its own language, the word in **bold**, each " +
                "followed by a translation into {lang} in *italics* on the next line.", kinds = setOf(Kind.WORD, Kind.PHRASE)),
            Action("explain", "Explain «{text}» in {lang}: what it means here, register, idioms and nuances. A passage: " +
                "what happens and what is implied, briefly. Short."),
        )
    }
}

class AiError(msg: String) : Exception(msg)

/** A model from the provider's list; price in USD per million tokens (input, output) when known. */
data class ModelInfo(val id: String, val name: String = id, val free: Boolean = false, val price: Pair<Double, Double>? = null,
                     val publisher: String = id.substringBefore('/', ""), val created: Long = 0, val context: Int = 0)

object Ai {
    private fun system(lang: String) = "You are a concise dictionary and language tutor inside an e-book reader on an " +
        "e-ink screen. Write every answer in $lang only. If the text is already in $lang, answer like an explanatory " +
        "dictionary of $lang instead of translating. Formatting: only **bold** and *italic*, short lines, simple \"- \" " +
        "lists. No headings, tables or code. Keep it under 120 words unless the request needs more."
    private val http = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
    private val cache = object : LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, String>) = size > 100
    }
    /** Server-side refusal fallback is offered for these Claude models. */
    private val FALLBACK = listOf("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5")

    fun cached(action: Action, text: String) = synchronized(cache) { cache[action.id + "\u0000" + text] }

    /** Blocking; call off the main thread. */
    fun ask(p: Provider, key: String, model: String, action: Action, text: String, lang: String): String {
        if (key.isBlank()) throw AiError("No API key for ${p.title}")
        if (model.isBlank()) throw AiError("No model selected for ${p.title}")
        val kind = Kind.of(text)
        val what = when (kind) { Kind.WORD -> "a single word"; Kind.PHRASE -> "a short phrase"
            Kind.PASSAGE -> "a passage of ${text.trim().split(Regex("\\s+")).size} words" }
        val prompt = "The selection is $what.\n" + action.prompt.replace("{text}", text).replace("{lang}", lang)
        val answer = stripThinking(if (p == Provider.ANTHROPIC) claude(key, model, prompt, system(lang)) else openAi(p, key, model, prompt, system(lang))).trim()
        synchronized(cache) { cache[action.id + "\u0000" + text] = answer }
        return answer
    }

    private fun claude(key: String, model: String, prompt: String, sys: String): String {
        val client = AnthropicOkHttpClient.builder().apiKey(key).build()
        val b = MessageCreateParams.builder().model(model).maxTokens(4096L).system(sys).addUserMessage(prompt)
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
        if (FALLBACK.any { model == it }) b.addBeta("server-side-fallback-2026-07-01").putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        val msg = try { client.beta().messages().create(b.build()) } finally { client.close() }
        if (msg.stopReason().map { it.toString() }.orElse("") == "refusal") throw AiError("The model declined this request")
        return msg.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n")
    }

    private fun openAi(p: Provider, key: String, model: String, prompt: String, sys: String): String {
        val body = JSONObject().put("model", model).put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", sys))
            .put(JSONObject().put("role", "user").put("content", prompt)))
        // OpenRouter: no reasoning pass, answers come in seconds; models that refuse the switch get a plain retry.
        if (p == Provider.OPENROUTER) body.put("reasoning", JSONObject().put("enabled", false))
        fun call(b: JSONObject): Pair<Int, String> {
            val req = Request.Builder().url(p.baseUrl + "/chat/completions").header("Authorization", "Bearer $key")
                .header("X-Title", "InkPeek").post(b.toString().toRequestBody("application/json".toMediaType())).build()
            return http.newCall(req).execute().use { it.code to it.body?.string().orEmpty() }
        }
        var (code, s) = call(body)
        if (code == 400 && body.has("reasoning")) { body.remove("reasoning"); call(body).let { code = it.first; s = it.second } }
        if (code !in 200..299) throw AiError("${p.title}: HTTP $code ${errorText(s)}")
        val choices = JSONObject(s).optJSONArray("choices") ?: throw AiError("${p.title}: ${errorText(s)}")
        return choices.getJSONObject(0).getJSONObject("message").optString("content")
    }

    private val NOT_CHAT = Regex("embed|whisper|tts|transcri|moderation|guard|safety|dall-e|image|audio|realtime|search|:batch$", RegexOption.IGNORE_CASE)

    /** Chat models the provider offers (OpenRouter tells names, prices and free variants). */
    fun models(p: Provider, key: String): List<ModelInfo> {
        if (key.isBlank() && p != Provider.OPENROUTER) throw AiError("No API key for ${p.title}")
        if (p == Provider.ANTHROPIC) {
            val client = AnthropicOkHttpClient.builder().apiKey(key).build()
            return try { client.models().list().autoPager().map { ModelInfo(it.id(), it.displayName()) }.toList() } finally { client.close() }
        }
        val req = Request.Builder().url(p.baseUrl + "/models").apply { if (key.isNotBlank()) header("Authorization", "Bearer $key") }.build()
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
