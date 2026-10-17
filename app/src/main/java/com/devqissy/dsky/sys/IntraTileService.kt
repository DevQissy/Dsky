package com.devqissy.dsky.sys

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.IBinder
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import androidx.annotation.RequiresApi
import com.devqissy.dsky.ui.MainActivity

@RequiresApi(api = Build.VERSION_CODES.N)
class IntraTileService : TileService() {

    override fun onStartListening() {
        val vpnState = VpnController.getState(this)
        val tile = qsTile ?: return
        tile.state = if (vpnState.activationRequested) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.updateTile()
    }

    override fun onClick() {
        val vpnState = VpnController.getState(this)
        if (vpnState.activationRequested) {
            VpnController.stop(this)
        } else {
            if (VpnService.prepare(this) == null) {
                VpnController.start(this)
            } else {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivityAndCollapseSafe(intent)
            }
        }
    }

    private fun startActivityAndCollapseSafe(intent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) { // API 34+
            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    override fun onBind(intent: Intent): IBinder? {
        TileService.requestListeningState(
            this, ComponentName(this, IntraTileService::class.java)
        )
        return super.onBind(intent)
    }
}