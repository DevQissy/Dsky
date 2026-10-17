package com.devqissy.dsky.sys

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.VpnService
import android.util.Log
import com.devqissy.dsky.ui.MainActivity

/**
 * Broadcast receiver that runs on boot, and also when the app is restarted due to an update.
 */
class AutoStarter : BroadcastReceiver() {

    companion object {
        private const val LOG_TAG = "AutoStarter"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED != intent.action) return

        Log.d(LOG_TAG, "Boot event")
        val state = VpnController.getState(context)

        if (state.activationRequested && !state.on) {
            Log.d(LOG_TAG, "Autostart enabled")
            if (VpnService.prepare(context) != null) {
                Log.w(LOG_TAG, "VPN permission not granted. Starting UI.")
                val startIntent = Intent(context, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(startIntent)
                return
            }
            VpnController.start(context)
        }
    }
}