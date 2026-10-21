package io.github.kafeg.inkpeek

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import okhttp3.ConnectionSpec
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

object Net {
    /** Downloads (packs, other dictionaries, the app and its releases): one client, TLS only. AI servers have their own. */
    val http: OkHttpClient = OkHttpClient.Builder().connectionSpecs(listOf(ConnectionSpec.MODERN_TLS)).readTimeout(60, TimeUnit.SECONDS).build()

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
