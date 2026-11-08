package com.hmedia.player.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

enum class NetKind { WIFI, CELLULAR, OFFLINE, OTHER }

// Reads the current connection type. Called when playback starts.
fun currentNetwork(context: Context): NetKind {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val net = cm.activeNetwork ?: return NetKind.OFFLINE
    val caps = cm.getNetworkCapabilities(net) ?: return NetKind.OFFLINE
    return when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetKind.WIFI
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetKind.CELLULAR
        else -> NetKind.OTHER
    }
}
