package io.github.kafeg.inkpeek.ui

import android.content.Context
import io.github.kafeg.inkpeek.Prefs
import io.github.kafeg.inkpeek.R
import io.github.kafeg.inkpeek.ai.Provider

/** A short, actionable message for an AI error instead of an exception name. */
fun aiError(ctx: Context, prefs: Prefs, online: Boolean, e: Throwable): String {
    if (e is io.github.kafeg.inkpeek.ai.Answers.Offline) return ctx.getString(R.string.err_offline)
    val chain = generateSequence(e) { it.cause }.take(8).toList()
    val name = e.javaClass.simpleName; val msg = chain.joinToString(" ") { it.message.orEmpty() }
    val net = chain.any { it is java.net.UnknownHostException || it is java.net.SocketException || it is java.net.SocketTimeoutException ||
        it is javax.net.ssl.SSLException || it is java.io.InterruptedIOException }
    val status = (e as? com.anthropic.errors.AnthropicServiceException)?.statusCode()
        ?: Regex("HTTP (\\d{3})").find(e.message.orEmpty())?.groupValues?.get(1)?.toInt() ?: 0
    // 403 or Gemini's 400 "User location is not supported": often a country block (VPN helps), sometimes the key.
    val region = status == 403 || Regex("(location|country|region|territory)[^.]{0,40}not supported|unsupported_country", RegexOption.IGNORE_CASE).containsMatchIn(msg)
    // Own server: online but it does not answer, or no address yet.
    if (prefs.provider == Provider.CUSTOM && ((net && online) || msg.startsWith("No server address")))
        return ctx.getString(R.string.err_server, prefs.url(Provider.CUSTOM).ifBlank { "—" }) + if (net) "\n\n($name)" else ""
    val id = when {
        !online -> R.string.err_offline
        net -> R.string.err_unreachable
        region -> R.string.err_region
        status == 401 -> R.string.err_key
        status == 429 -> R.string.err_rate
        status == 402 || msg.contains("credit", true) -> R.string.err_credits
        msg.startsWith("No API key") -> R.string.err_no_key
        msg.startsWith("No model") -> R.string.err_no_model
        else -> R.string.err_other
    }
    val t = ctx.getString(id)
    return if (id == R.string.err_other || id == R.string.err_region) "$t\n\n($name${if (e.message.isNullOrBlank()) "" else ": " + e.message!!.take(160)})" else t
}
