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
    OPENROUTER("OpenRouter", "https://openrouter.ai/api/v1", ""),
}

/** An AI tab of the card. [id] keys its label and description in resources; [prompt] uses {text} and {lang}. */
data class Action(val id: String, val prompt: String, val onByDefault: Boolean = true) {
    companion object {
        val ALL = listOf(
            Action("translate", "Translate into {lang}: «{text}».\n" +
                "A single word: first line **base form** and part of speech (for nouns: gender and plural), then the " +
                "1–3 main meanings in *italics*; say so if the word is an inflected form.\n" +
                "A phrase or sentence: only a natural translation, nothing else."),
            Action("grammar", "Explain in {lang} how «{text}» is built and why, at most 7 short lines: word order (where " +
                "the verb stands and why), which word requires which case or form, tense and mood, and the rule behind " +
                "it. Put the words you talk about in **bold**. No headings."),
            Action("words", "Vocabulary of «{text}», in {lang}.\nA phrase: list the words a learner may not know, one per " +
                "line: **base form** — short meaning. Skip very common words.\nA single word: **base form**, 2–3 " +
                "synonyms, related words, each with a short meaning."),
            Action("examples", "3 short everyday sentences with «{text}» in its own language, the word in **bold**, each " +
                "followed by a translation into {lang} in *italics* on the next line."),
            Action("explain", "Explain «{text}» in {lang}: what it means here, register, idioms and nuances. Short.", false),
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
        val prompt = action.prompt.replace("{text}", text).replace("{lang}", lang)
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
        val req = Request.Builder().url(p.baseUrl + "/chat/completions").header("Authorization", "Bearer $key")
            .header("X-Title", "InkPeek").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw AiError("${p.title}: HTTP ${r.code} ${errorText(s)}")
            return JSONObject(s).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
        }
    }

    private val NOT_CHAT = Regex("embed|whisper|tts|transcri|moderation|dall-e|image|audio|realtime|search", RegexOption.IGNORE_CASE)

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
