package com.vinaykaushik.batteryalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ChargingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {

            Intent.ACTION_POWER_CONNECTED -> {
                if (PrefsManager.getInstance(context).masterEnabled) {
                    startChargingService(context)
                }
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                context.stopService(
                    Intent(context, ChargingService::class.java)
                )
            }

//            Intent.ACTION_BOOT_COMPLETED -> {
//                // Only re-arm if the master toggle is on
//                if (PrefsManager.getInstance(context).masterEnabled) {
//                    startChargingService(context)
//                }
//            }
            // no auto-start on boot needed so i commented it.
        }
    }

    private fun startChargingService(context: Context) {
        val serviceIntent = Intent(context, ChargingService::class.java)
        ContextCompat.startForegroundService(context, serviceIntent)
    }
}