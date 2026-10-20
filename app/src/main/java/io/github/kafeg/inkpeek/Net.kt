package io.github.kafeg.inkpeek

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

object Net {
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
