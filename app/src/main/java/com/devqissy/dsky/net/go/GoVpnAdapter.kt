package com.devqissy.dsky.net.go

import android.content.Context
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import android.util.Log
import com.devqissy.dsky.R
import com.devqissy.dsky.data.ServerRepository
import com.devqissy.dsky.sys.CountryCode
import com.devqissy.dsky.sys.IntraVpnService
import com.devqissy.dsky.sys.PersistentState
import com.devqissy.dsky.sys.VpnController
import backend.Backend
import backend.DoHServer
import backend.Session
import protect.Protector
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.Locale

/**
 * VpnAdapter that captures all traffic and routes it through a go-tun2socks instance.
 */
class GoVpnAdapter private constructor(
    private val vpnService: IntraVpnService,
    private var tunFd: ParcelFileDescriptor?
) {
    companion object {
        private const val LOG_TAG = "GoVpnAdapter"
        private const val VPN_INTERFACE_MTU = 1380
        private const val DNS_DEFAULT_PORT = 53
        private const val IPV4_TEMPLATE = "10.111.222.%d"
        private const val IPV4_PREFIX_LENGTH = 24
        private const val IPV6_TEMPLATE = "fd00::%d"
        private const val CHOIR_FILENAME = "choir-salt"

        private enum class LanIp(val value: Int) {
            GATEWAY(1), ROUTER(2), DNS(3);
            fun make(template: String) = String.format(Locale.ROOT, template, value)
        }

        @JvmField
        val FAKE_DNS_IP: String = LanIp.DNS.make(IPV4_TEMPLATE)

        @JvmStatic
        fun establish(vpnService: IntraVpnService): GoVpnAdapter? {
            val tunFd = establishVpn(vpnService) ?: return null
            return GoVpnAdapter(vpnService, tunFd)
        }

        private fun establishVpn(vpnService: IntraVpnService): ParcelFileDescriptor? {
            return try {
                val builder = vpnService.newBuilder()
                    .setSession("Intra High-Speed VPN")
                    .setMtu(VPN_INTERFACE_MTU)
                    .addAddress(LanIp.GATEWAY.make(IPV4_TEMPLATE), IPV4_PREFIX_LENGTH)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("1.1.1.1")
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("2606:4700:4700::1111")
                    .addDnsServer(LanIp.DNS.make(IPV4_TEMPLATE))
                    .addDnsServer(LanIp.DNS.make(IPV6_TEMPLATE))

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    builder.addDisallowedApplication(vpnService.packageName)
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    builder.setMetered(false)
                }
                builder.setBlocking(false)
                builder.establish()
            } catch (e: Exception) {
                Log.e(LOG_TAG, "Failed to establish VPN", e)
                null
            }
        }

        @JvmStatic
        fun getIpString(context: Context, url: String?): String {
            if (url.isNullOrEmpty()) return ""
            val servers = ServerRepository.getServers(context)
            var ret = servers.firstOrNull { url.contains(it.url) }?.ipsForMode("both") ?: ""

            return ret
        }
    }

    private var session: Session? = null
    private var listener: GoIntraListener? = null

    @Synchronized
    fun start() = connectTunnel()

    private fun connectTunnel() {
        if (session != null) return
        val fakeDns = "$FAKE_DNS_IP:$DNS_DEFAULT_PORT"
        listener = GoIntraListener(vpnService)
        val dohURL = PersistentState.getServerUrl(vpnService)

        try {
            Log.i(LOG_TAG, "Starting go-tun2socks")
            val server = makeDoHServer(dohURL)
            session = Backend.connectSession(
                tunFd!!.fd.toLong(),
                fakeDns,
                server,
                getProtector(),
                listener
            )
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to start go-tun2socks", e)
            VpnController.onConnectionStateChanged(vpnService, IntraVpnService.State.FAILING)
        }
    }

    private fun enableChoir() {
        val countryCode = CountryCode(vpnService)
        val country = countryCode.networkCountry.ifEmpty { countryCode.deviceCountry }
        if (country.isEmpty()) {
            Log.i(LOG_TAG, "No country code found")
            return
        }
        val file = "${vpnService.filesDir}${File.separator}$CHOIR_FILENAME"
        try {
            session?.enableSNIReporter(file, "dsky.metrics.gstatic.com", country)
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to enable SNI reporter", e)
        }
    }

    private fun getProtector(): Protector? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) return null
        return vpnService
    }

    @Synchronized
    fun close() {
        session?.disconnect()
        try {
            tunFd?.close()
        } catch (e: IOException) {
            Log.e(LOG_TAG, "Failed to close tunFd", e)
        }
        tunFd = null
    }

    private fun makeDoHServer(url: String?): DoHServer {
        val realUrl = PersistentState.expandUrl(vpnService, url)
        val dohIPs = getIpString(vpnService, realUrl)
        val host = URL(realUrl).host
        val startTime = SystemClock.elapsedRealtime()
        return try {
            DoHServer(realUrl, dohIPs, getProtector(), listener).also {
                val delta = (SystemClock.elapsedRealtime() - startTime).toInt()
            }
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Bootstrap failed for $host", e)
            throw e
        }
    }

    @Synchronized
    fun updateDohUrl() {
        if (tunFd == null) return
        if (session == null) {
            connectTunnel()
            return
        }
        val url = PersistentState.getServerUrl(vpnService)
        try {
            session!!.setDoHServer(makeDoHServer(url))
        } catch (e: Exception) {
            Log.e(LOG_TAG, "Failed to update DoH URL", e)
            session!!.disconnect()
            session = null
            VpnController.onConnectionStateChanged(vpnService, IntraVpnService.State.FAILING)
        }
    }
}