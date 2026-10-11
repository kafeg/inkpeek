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

/** A button in the card: [prompt] may use {text} and {lang}. */
data class Action(val name: String, val prompt: String) {
    companion object {
        val DEFAULTS = listOf(
            Action("Translate", "Translate into {lang}: «{text}». For a single word: main senses, part of speech, " +
                "gender and plural for nouns, the base form if it is an inflected form."),
            Action("Grammar", "Explain the grammar of «{text}» in {lang}, briefly: forms, case, tense, word order."),
            Action("Explain", "Explain the meaning and usage of «{text}» in {lang}: register, idioms, nuances."),
            Action("Examples", "Give 3 short example sentences with «{text}» in its language, each with a translation into {lang}."),
        )
    }
}

class AiError(msg: String) : Exception(msg)

object Ai {
    private const val SYSTEM = "You are a concise dictionary and language tutor inside an e-book reader on an e-ink " +
        "screen. Answer in plain text without Markdown tables, headings or emphasis marks. Keep it under 120 words " +
        "unless the request needs more."
    private val http = OkHttpClient.Builder().callTimeout(45, TimeUnit.SECONDS).build()
    private val cache = object : LinkedHashMap<String, String>(128, 0.75f, true) {
        override fun removeEldestEntry(e: MutableMap.MutableEntry<String, String>) = size > 100
    }
    /** Server-side refusal fallback is offered for these Claude models. */
    private val FALLBACK = listOf("claude-fable-5-1", "claude-opus-5-5", "claude-opus-5", "claude-sonnet-5-5")

    fun cached(action: Action, text: String) = synchronized(cache) { cache[action.name + "\u0000" + text] }

    /** Blocking; call off the main thread. */
    fun ask(p: Provider, key: String, model: String, action: Action, text: String, lang: String): String {
        if (key.isBlank()) throw AiError("No API key for ${p.title}")
        if (model.isBlank()) throw AiError("No model selected for ${p.title}")
        val prompt = action.prompt.replace("{text}", text).replace("{lang}", lang)
        val answer = stripThinking(if (p == Provider.ANTHROPIC) claude(key, model, prompt) else openAi(p, key, model, prompt)).trim()
        synchronized(cache) { cache[action.name + "\u0000" + text] = answer }
        return answer
    }

    private fun claude(key: String, model: String, prompt: String): String {
        val client = AnthropicOkHttpClient.builder().apiKey(key).build()
        val b = MessageCreateParams.builder().model(model).maxTokens(4096L).system(SYSTEM).addUserMessage(prompt)
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
        if (FALLBACK.any { model == it }) b.addBeta("server-side-fallback-2026-07-01").putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
        val msg = try { client.beta().messages().create(b.build()) } finally { client.close() }
        if (msg.stopReason().map { it.toString() }.orElse("") == "refusal") throw AiError("The model declined this request")
        return msg.content().mapNotNull { it.text().orElse(null)?.text() }.joinToString("\n")
    }

    private fun openAi(p: Provider, key: String, model: String, prompt: String): String {
        val body = JSONObject().put("model", model).put("messages", JSONArray()
            .put(JSONObject().put("role", "system").put("content", SYSTEM))
            .put(JSONObject().put("role", "user").put("content", prompt)))
        val req = Request.Builder().url(p.baseUrl + "/chat/completions").header("Authorization", "Bearer $key")
            .header("X-Title", "InkPeek").post(body.toString().toRequestBody("application/json".toMediaType())).build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw AiError("${p.title}: HTTP ${r.code} ${errorText(s)}")
            return JSONObject(s).getJSONArray("choices").getJSONObject(0).getJSONObject("message").optString("content")
        }
    }

    /** Model ids the provider offers. */
    fun models(p: Provider, key: String): List<String> {
        if (key.isBlank()) throw AiError("No API key for ${p.title}")
        if (p == Provider.ANTHROPIC) {
            val client = AnthropicOkHttpClient.builder().apiKey(key).build()
            return try { client.models().list().autoPager().map { it.id() }.toList() } finally { client.close() }
        }
        val req = Request.Builder().url(p.baseUrl + "/models").header("Authorization", "Bearer $key").build()
        http.newCall(req).execute().use { r ->
            val s = r.body?.string().orEmpty()
            if (!r.isSuccessful) throw AiError("${p.title}: HTTP ${r.code} ${errorText(s)}")
            val a = JSONObject(s).getJSONArray("data")
            return (0 until a.length()).map { a.getJSONObject(it).getString("id") }.sorted()
        }
    }

    private fun errorText(s: String) = runCatching { JSONObject(s).getJSONObject("error").optString("message") }.getOrDefault(s.take(200))

    /** Removes <think>…</think> blocks some reasoning models put inline. */
    fun stripThinking(s: String) = s.replace(Regex("(?s)<think>.*?</think>"), "")
}
