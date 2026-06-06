package com.vinaykaushik.batteryalarm

import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import android.widget.Toast

class ChargingTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        val prefs = PrefsManager.getInstance(this)

        if (!prefs.masterEnabled) {
            Toast.makeText(this, "Enable master switch first", Toast.LENGTH_SHORT).show()
            qsTile.state = Tile.STATE_INACTIVE
            qsTile.updateTile()
            return
        }

        val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val isCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0

        if (!isCharging) {
            Toast.makeText(this, "Phone not charging", Toast.LENGTH_SHORT).show()
            qsTile.state = Tile.STATE_INACTIVE
            qsTile.updateTile()
            return
        }

        // All good — start service
        startForegroundService(Intent(this, ChargingService::class.java))
        qsTile.state = Tile.STATE_ACTIVE
        qsTile.icon = android.graphics.drawable.Icon.createWithResource(
            this, android.R.drawable.ic_lock_idle_alarm
        )
        qsTile.updateTile()
    }

    private fun updateTileState() {
        qsTile.state = Tile.STATE_INACTIVE
        qsTile.icon = android.graphics.drawable.Icon.createWithResource(
            this, android.R.drawable.ic_lock_idle_alarm
        )
        qsTile.updateTile()
    }
}