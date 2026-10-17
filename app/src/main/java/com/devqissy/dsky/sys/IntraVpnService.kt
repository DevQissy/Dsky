package com.devqissy.dsky.sys

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.preference.PreferenceManager
import android.service.quicksettings.TileService
import android.text.TextUtils
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.annotation.WorkerThread
import com.devqissy.dsky.R
import com.devqissy.dsky.net.doh.Transaction
import com.devqissy.dsky.net.go.GoVpnAdapter
import com.devqissy.dsky.ui.MainActivity
import protect.Protector
import java.net.InetAddress
import java.util.Calendar

class IntraVpnService : VpnService(), NetworkManager.NetworkListener,
    SharedPreferences.OnSharedPreferenceChangeListener, Protector {

    enum class State { NEW, WORKING, FAILING }

    companion object {
        private const val LOG_TAG = "IntraVpnService"
        private const val SERVICE_ID = 1
        private const val MAIN_CHANNEL_ID = "vpn"
        private const val WARNING_CHANNEL_ID = "warning"
        private const val NO_PENDING_CONNECTION = "This value is not a possible URL."
    }

    @GuardedBy("VpnController")
    private var networkManager: NetworkManager? = null
    private var networkConnected = false

    @GuardedBy("VpnController")
    private var vpnAdapter: GoVpnAdapter? = null

    @GuardedBy("VpnController")
    private var url: String? = null

    @GuardedBy("VpnController")
    private var pendingUrl: String = NO_PENDING_CONNECTION

    fun isOn(): Boolean = vpnAdapter != null

    override fun onSharedPreferenceChanged(preferences: SharedPreferences, key: String?) {
        if (PersistentState.APPS_KEY == key && vpnAdapter != null) {
            restartVpn()
        }
        if (PersistentState.URL_KEY == key) {
            val newUrl = PersistentState.getServerUrl(this)
            if (!newUrl.isNullOrEmpty() && newUrl != "null") {
                url = newUrl
                spawnServerUpdate()
            }
        }
    }

    private fun spawnServerUpdate() {
        synchronized(VpnController) {
            if (networkManager != null) {
                Thread({ updateServerConnection() }, "updateServerConnection-onStartCommand").start()
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        synchronized(VpnController) {
            url = PersistentState.getServerUrl(this)
            if (url.isNullOrEmpty() || url == "null") {
                url = "https://dns.google/dns-query"
            }
            Log.i(LOG_TAG, "Starting DNS VPN service, url=$url")

            PreferenceManager.getDefaultSharedPreferences(this)
                .registerOnSharedPreferenceChangeListener(this)

            if (networkManager != null) {
                spawnServerUpdate()
                return START_REDELIVER_INTENT
            }

            networkManager = NetworkManager(this, this)

            val mainActivityIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val builder: Notification.Builder
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    MAIN_CHANNEL_ID,
                    getString(R.string.channel_name),
                    NotificationManager.IMPORTANCE_LOW
                ).apply { description = getString(R.string.channel_description) }
                getSystemService(NotificationManager::class.java)
                    .createNotificationChannel(channel)
                builder = Notification.Builder(this, MAIN_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                builder = Notification.Builder(this).setPriority(Notification.PRIORITY_MIN)
            }

            builder.setSmallIcon(R.drawable.ic_moon)
                .setContentTitle(resources.getText(R.string.notification_title))
                .setContentText(resources.getText(R.string.notification_content))
                .setContentIntent(mainActivityIntent)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.setVisibility(Notification.VISIBILITY_SECRET)
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                startForeground(
                    SERVICE_ID, builder.build(),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE
                )
            } else {
                startForeground(SERVICE_ID, builder.build())
            }

            updateQuickSettingsTile()
            return START_REDELIVER_INTENT
        }
    }

    @WorkerThread
    private fun updateServerConnection() {
        synchronized(VpnController) {
            vpnAdapter?.updateDohUrl()
        }
    }

    @WorkerThread
    private fun startVpn() {
        synchronized(VpnController) {
            if (vpnAdapter != null) return
            startVpnAdapter()
            VpnController.onStartComplete(this, vpnAdapter != null)
            if (vpnAdapter == null) {
                Log.w(LOG_TAG, "Failed to start VPN adapter")
                stopSelf()
            }
        }
    }

    private fun restartVpn() {
        synchronized(VpnController) {
            val oldAdapter = vpnAdapter
            vpnAdapter = makeVpnAdapter()
            oldAdapter?.close()
            val adapter = vpnAdapter
            if (adapter != null) {
                adapter.start()
            } else {
                Log.w(LOG_TAG, "Restart failed")
            }
        }
    }

    override fun onCreate() {
        Log.i(LOG_TAG, "Creating DNS VPN service")
        VpnController.setIntraVpnService(this)
        syncNumRequests()
    }

    fun signalStopService(userInitiated: Boolean) {
        Log.i(LOG_TAG, "Received stop signal. User initiated: $userInitiated")

        if (!userInitiated) {
            val vibrationPattern = longArrayOf(1000)
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE)
                    as NotificationManager

            val builder: Notification.Builder
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    WARNING_CHANNEL_ID,
                    getString(R.string.warning_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = getString(R.string.warning_channel_description)
                    enableVibration(true)
                    setVibrationPattern(vibrationPattern)
                }
                notificationManager.createNotificationChannel(channel)
                builder = Notification.Builder(this, WARNING_CHANNEL_ID)
            } else {
                @Suppress("DEPRECATION")
                builder = Notification.Builder(this)
                    .setVibrate(vibrationPattern)
                    .setPriority(Notification.PRIORITY_MAX)
            }

            val mainActivityIntent = PendingIntent.getActivity(
                this, 0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            builder.setSmallIcon(R.drawable.ic_moon)
                .setContentTitle(resources.getText(R.string.warning_title))
                .setContentText(resources.getText(R.string.notification_content))
                .setContentIntent(mainActivityIntent)
                .setAutoCancel(true)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                builder.setCategory(Notification.CATEGORY_ERROR)
            }

            notificationManager.notify(0, builder.build())
        }

        stopVpnAdapter()
        stopSelf()
        updateQuickSettingsTile()
    }

    private fun makeVpnAdapter(): GoVpnAdapter? = GoVpnAdapter.establish(this)

    private fun startVpnAdapter() {
        synchronized(VpnController) {
            if (vpnAdapter == null) {
                Log.i(LOG_TAG, "Starting VPN adapter")
                vpnAdapter = makeVpnAdapter()
                val adapter = vpnAdapter
                if (adapter != null) {
                    adapter.start()
                } else {
                    Log.e(LOG_TAG, "Failed to start VPN adapter!")
                }
            }
        }
    }

    private fun stopVpnAdapter() {
        synchronized(VpnController) {
            val adapter = vpnAdapter
            if (adapter != null) {
                adapter.close()
                vpnAdapter = null
                VpnController.onConnectionStateChanged(this, null)
            }
        }
    }

    private fun updateQuickSettingsTile() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                TileService.requestListeningState(
                    this, ComponentName(this, IntraTileService::class.java)
                )
            } catch (ignored: SecurityException) {
            } catch (ignored: IllegalArgumentException) {
            }
        }
    }

    override fun onDestroy() {
        synchronized(VpnController) {
            Log.i(LOG_TAG, "Destroying DNS VPN service")
            PreferenceManager.getDefaultSharedPreferences(this)
                .unregisterOnSharedPreferenceChangeListener(this)
            networkManager?.destroy()
            syncNumRequests()
            VpnController.setIntraVpnService(null)
            @Suppress("DEPRECATION")
            stopForeground(true)
            if (vpnAdapter != null) signalStopService(false)
        }
    }

    override fun onRevoke() {
        Log.w(LOG_TAG, "VPN service revoked.")
        stopSelf()
        PersistentState.setVpnEnabled(this, false)
    }

    fun newBuilder(): VpnService.Builder {
        val builder = Builder()
            .setSession("Intra")
            .setMtu(1380)
            .addAddress("10.1.1.1", 24)
            .addAddress("fd00:1:fd00:1:fd00:1:fd00:1", 128)
            .addRoute("0.0.0.0", 0)
            .addRoute("::", 0)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            builder.allowBypass()
            try {
                builder.addDisallowedApplication(packageName)
                for (pkg in PersistentState.getExcludedPackages(this)) {
                    builder.addDisallowedApplication(pkg)
                }
            } catch (e: PackageManager.NameNotFoundException) {
                Log.e(LOG_TAG, "Failed to exclude apps", e)
            }
        }
        return builder
    }

    fun recordTransaction(transaction: Transaction) {
        transaction.responseTime = SystemClock.elapsedRealtime()
        transaction.responseCalendar = Calendar.getInstance()

        getTracker().recordTransaction(this, transaction)

        VpnController.getEventViewModel()?.sendTransaction(transaction)

        if (!networkConnected) return

        when {
            isOn() -> VpnController.onConnectionStateChanged(this, State.WORKING)
            transaction.status == Transaction.Status.COMPLETE ->
                VpnController.onConnectionStateChanged(this, State.WORKING)
            transaction.status != Transaction.Status.CANCELED ->
                if (!isOn()) VpnController.onConnectionStateChanged(this, State.FAILING)
        }
    }

    private fun getTracker(): QueryTracker = VpnController.getTracker(this)

    private fun syncNumRequests() = getTracker().sync(this)

    private fun setNetworkConnected(connected: Boolean) {
        networkConnected = connected
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val activeNetwork = getSystemService(ConnectivityManager::class.java).activeNetwork
            setUnderlyingNetworks(if (connected) arrayOf(activeNetwork) else null)
        }
    }

    override fun onNetworkConnected(network: Network) {
        Log.i(LOG_TAG, "Connected event.")
        setNetworkConnected(true)
        if (isOn()) VpnController.onConnectionStateChanged(this, State.WORKING)
        Thread({
            updateServerConnection()
            startVpn()
        }, "startVpn-onNetworkConnected").start()
    }

    override fun onNetworkDisconnected() {
        Log.i(LOG_TAG, "Disconnected event.")
        setNetworkConnected(false)
        VpnController.onConnectionStateChanged(this, null)
    }

    override fun getResolvers(): String {
        val ips = mutableListOf<String>()
        val resolvers: List<InetAddress> = networkManager?.getSystemResolvers() ?: emptyList()
        for (ip in resolvers) {
            val address = ip.hostAddress ?: continue
            if (GoVpnAdapter.FAKE_DNS_IP != address) {
                ips.add(address)
            }
        }
        return TextUtils.join(",", ips)
    }
}