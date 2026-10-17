package com.devqissy.dsky.sys

import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStoreOwner

object VpnController {

    private var intraVpnService: IntraVpnService? = null
    private var connectionState: IntraVpnService.State? = null
    private var tracker: QueryTracker? = null
    private var eventViewModel: VpnEventViewModel? = null

    @JvmStatic
    fun getInstance(): VpnController = this

    @JvmStatic
    fun setIntraVpnService(service: IntraVpnService?) {
        intraVpnService = service
    }

    @JvmStatic
    fun getIntraVpnService(): IntraVpnService? = intraVpnService

    @JvmStatic
    fun initEventViewModel(owner: ViewModelStoreOwner) {
        eventViewModel = ViewModelProvider(owner)[VpnEventViewModel::class.java]
    }


    @JvmStatic
    fun getEventViewModel(): VpnEventViewModel? = eventViewModel

    @JvmStatic
    @Synchronized
    fun onConnectionStateChanged(context: Context, state: IntraVpnService.State?) {
        if (intraVpnService == null) {
            return
        }
        connectionState = state
        stateChanged(context)
    }

    private fun stateChanged(context: Context) {
        eventViewModel?.sendDnsStatus()
    }

    @JvmStatic
    @Synchronized
    fun getTracker(context: Context): QueryTracker {
        if (tracker == null) tracker = QueryTracker(context)
        return tracker!!
    }

    @JvmStatic
    @Synchronized
    fun start(context: Context) {
        if (intraVpnService != null) return
        PersistentState.setVpnEnabled(context, true)
        stateChanged(context)

        val url = PersistentState.getServerUrl(context)
        val startServiceIntent = Intent(context, IntraVpnService::class.java).apply {
            putExtra("extra_url", url)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(startServiceIntent)
        } else {
            context.startService(startServiceIntent)
        }
    }

    @JvmStatic
    @Synchronized
    fun onStartComplete(context: Context, succeeded: Boolean) {
        if (!succeeded) {
            stop(context)
        } else {
            stateChanged(context)
        }
    }

    @JvmStatic
    @Synchronized
    fun stop(context: Context) {
        PersistentState.setVpnEnabled(context, false)
        connectionState = null
        intraVpnService?.signalStopService(true)
        intraVpnService = null
        stateChanged(context)
    }

    @JvmStatic
    @Synchronized
    fun getState(context: Context): VpnState {
        val requested = PersistentState.getVpnEnabled(context)
        val on = intraVpnService?.isOn() == true
        return VpnState(requested, on, connectionState)
    }
}