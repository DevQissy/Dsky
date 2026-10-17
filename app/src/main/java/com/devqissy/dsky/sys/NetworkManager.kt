package com.devqissy.dsky.sys

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.util.Log
import java.net.InetAddress

class NetworkManager(context: Context, private var networkListener: NetworkListener?) {

    interface NetworkListener {
        fun onNetworkConnected(network: Network)
        fun onNetworkDisconnected()
    }

    companion object {
        private const val LOG_TAG = "NetworkManager"
    }

    private val applicationContext: Context = context.applicationContext
    private val connectivityManager: ConnectivityManager =
        applicationContext.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var isConnected = false

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    Log.i(LOG_TAG, "Network available: $network")
                    isConnected = true
                    networkListener?.onNetworkConnected(network)
                }

                override fun onLost(network: Network) {
                    Log.i(LOG_TAG, "Network lost: $network")
                    isConnected = false
                    networkListener?.onNetworkDisconnected()
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    Log.d(LOG_TAG, "Network capabilities changed: $network")
                }
            }

            val networkRequest = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()

            try {
                connectivityManager.registerNetworkCallback(networkRequest, networkCallback!!)
            } catch (e: SecurityException) {
                Log.e(LOG_TAG, "Failed to register network callback", e)
            }

            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                isConnected = true
                networkListener?.onNetworkConnected(activeNetwork)
            }
        } else {
            Log.w(LOG_TAG, "API level < 21, network monitoring not fully supported")
        }
    }

    fun destroy() {
        networkCallback?.let {
            try {
                connectivityManager.unregisterNetworkCallback(it)
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Error unregistering network callback", e)
            }
            networkCallback = null
        }
    }

    fun getSystemResolvers(): List<InetAddress> {
        val resolvers = mutableListOf<InetAddress>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = connectivityManager.activeNetwork
            if (activeNetwork != null) {
                val linkProperties = connectivityManager.getLinkProperties(activeNetwork)
                linkProperties?.dnsServers?.let {
                    resolvers.addAll(it)
                }
            }
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try {
                val activeNetwork = connectivityManager.activeNetwork
                if (activeNetwork != null) {
                    val getLinkProperties = ConnectivityManager::class.java
                        .getMethod("getLinkProperties", Network::class.java)
                    val linkProperties = getLinkProperties.invoke(connectivityManager, activeNetwork)
                    if (linkProperties != null) {
                        val getDnses = linkProperties.javaClass.getMethod("getDnses")
                        @Suppress("UNCHECKED_CAST")
                        val addresses = getDnses.invoke(linkProperties) as? Collection<InetAddress>
                        addresses?.let { resolvers.addAll(it) }
                    }
                }
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to get system resolvers", e)
            }
        }

        return resolvers
    }
}