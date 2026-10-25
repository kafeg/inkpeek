package io.github.kafeg.inkpeek

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.FilterInputStream
import java.io.InputStream
import java.util.concurrent.TimeUnit

object Net {
    /** Downloads (packs, other dictionaries, the app and its releases): one client, TLS only. AI servers have their own. */
    val http: OkHttpClient = OkHttpClient.Builder().connectionSpecs(listOf(ConnectionSpec.MODERN_TLS)).readTimeout(60, TimeUnit.SECONDS).build()

    /** A page as text (a list, a release); an error for anything but 2xx. Blocking. */
    fun text(url: String, accept: String? = null, client: OkHttpClient = http): String =
        client.newCall(Request.Builder().url(url).apply { accept?.let { header("Accept", it) } }.build()).execute().use {
            if (!it.isSuccessful) error("HTTP ${it.code}: ${url.substringAfterLast('/')}"); it.body!!.string()
        }

    /**
     * A download handed to [consume] as a stream; [progress] gets the bytes read and the total (the server's length,
     * else [size]). Blocking.
     */
    fun <T> download(url: String, size: Long, progress: (Long, Long) -> Unit, client: OkHttpClient = http, consume: (InputStream) -> T): T =
        client.newCall(Request.Builder().url(url).build()).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}: ${url.substringAfterLast('/')}")
            val body = r.body!!; val all = body.contentLength().takeIf { it > 0 } ?: size
            consume(object : FilterInputStream(body.byteStream()) {
                var done = 0L
                fun tick(n: Int) { if (n > 0) { done += n; progress(done, all) } }
                override fun read(): Int = super.read().also { if (it >= 0) tick(1) }
                override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { tick(it) }
            })
        }

    /** A validated network; a VPN counts only with a real network under it (it stays "validated" after Wi-Fi goes off). */
    fun online(ctx: Context): Boolean {
        val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val active = cm.getNetworkCapabilities(cm.activeNetwork ?: return false) ?: return false
        if (!active.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) return false
        @Suppress("DEPRECATION") return active.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) || cm.allNetworks.any { n ->
            cm.getNetworkCapabilities(n)?.let { it.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) &&
                it.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) } == true
        }
    }
}
