package com.taskscheduling.app.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

enum class NetworkTransportType {
    WIFI, CELLULAR, ETHERNET, OTHER, NONE
}

data class NetworkStatus(
    val isConnected: Boolean,
    val transportType: NetworkTransportType = NetworkTransportType.NONE,
    val hasValidatedInternet: Boolean = false
) {
    val displayLabel: String
        get() = if (isConnected) {
            when (transportType) {
                NetworkTransportType.WIFI -> "Online (Wi-Fi)"
                NetworkTransportType.CELLULAR -> "Online (Cellular)"
                NetworkTransportType.ETHERNET -> "Online (Ethernet)"
                else -> "Online"
            }
        } else {
            "Network Offline"
        }
}

/**
 * Real-time Android [ConnectivityManager] observer that monitors network status changes.
 * Differentiates network availability from local offline automation readiness.
 */
class NetworkConnectivityMonitor(private val context: Context) {

    private val connectivityManager =
        context.applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    val networkStatus: Flow<NetworkStatus> = callbackFlow {
        val cm = connectivityManager
        if (cm == null) {
            trySend(NetworkStatus(isConnected = false))
            close()
            return@callbackFlow
        }

        // Emit initial status
        trySend(getCurrentNetworkStatus())

        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(getCurrentNetworkStatus())
            }

            override fun onLost(network: Network) {
                trySend(getCurrentNetworkStatus())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(getCurrentNetworkStatus())
            }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()

        try {
            cm.registerNetworkCallback(request, networkCallback)
        } catch (_: Exception) {
            trySend(getCurrentNetworkStatus())
        }

        awaitClose {
            try {
                cm.unregisterNetworkCallback(networkCallback)
            } catch (_: Exception) {}
        }
    }.distinctUntilChanged()

    fun getCurrentNetworkStatus(): NetworkStatus {
        val cm = connectivityManager ?: return NetworkStatus(isConnected = false)
        return try {
            val activeNetwork = cm.activeNetwork ?: return NetworkStatus(isConnected = false)
            val capabilities = cm.getNetworkCapabilities(activeNetwork) ?: return NetworkStatus(isConnected = false)

            val hasInternet = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val isValidated = capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)

            val transport = when {
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> NetworkTransportType.WIFI
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> NetworkTransportType.CELLULAR
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkTransportType.ETHERNET
                else -> NetworkTransportType.OTHER
            }

            NetworkStatus(
                isConnected = hasInternet,
                transportType = transport,
                hasValidatedInternet = isValidated
            )
        } catch (_: Throwable) {
            NetworkStatus(isConnected = false)
        }
    }

    companion object {
        @Volatile
        private var instance: NetworkConnectivityMonitor? = null

        fun getInstance(context: Context): NetworkConnectivityMonitor {
            return instance ?: synchronized(this) {
                instance ?: NetworkConnectivityMonitor(context.applicationContext).also { instance = it }
            }
        }
    }
}
