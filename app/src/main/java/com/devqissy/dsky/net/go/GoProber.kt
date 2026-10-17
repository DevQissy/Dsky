package com.devqissy.dsky.net.go

import android.content.Context
import android.os.Build
import com.devqissy.dsky.net.doh.Prober
import com.devqissy.dsky.sys.VpnController
import backend.Backend
import backend.DoHServer
import protect.Protector

/**
 * Implements a Probe using the Go-based DoH client.
 */
class GoProber(private val context: Context) : Prober() {

    override fun probe(url: String, callback: Callback) {
        Thread {
            val dohIPs = GoVpnAdapter.getIpString(context, url)
            try {
                val protector: Protector? =
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) null
                    else VpnController.getIntraVpnService()
                Backend.probe(DoHServer(url, dohIPs, protector, null))
                callback.onCompleted(true)
            } catch (e: Exception) {
                callback.onCompleted(false)
            }
        }.start()
    }
}
