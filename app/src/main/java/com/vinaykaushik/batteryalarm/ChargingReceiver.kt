package com.vinaykaushik.batteryalarm

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat

class ChargingReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        Log.d("BatteryAlarm", "ChargingReceiver: action received = ${intent.action}")
        when (intent.action) {

            Intent.ACTION_POWER_CONNECTED -> {
                Log.d("BatteryAlarm", "ChargingReceiver: POWER_CONNECTED — masterEnabled = ${PrefsManager.getInstance(context).masterEnabled}")
                if (PrefsManager.getInstance(context).masterEnabled) {
                    startChargingService(context)
                }
            }
            Intent.ACTION_POWER_DISCONNECTED -> {
                Log.d("BatteryAlarm", "ChargingReceiver: POWER_DISCONNECTED — stopping service")
                val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                nm.cancel(NotificationHelper.NOTIF_ID_ALARM)
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