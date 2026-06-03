package com.vinaykaushik.batteryalarm

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_SERVICE = "channel_service"
        const val CHANNEL_ALARM   = "channel_alarm"

        const val NOTIF_ID_SERVICE = 1
        const val NOTIF_ID_ALARM   = 2
    }

    init {
        createChannels()
    }

    // ── Channel setup ────────────────────────────────────────────────────────

    private fun createChannels() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Silent persistent channel for the foreground service
        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "Battery Alarm Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Silent notification shown while charging is monitored"
            setSound(null, null)
            enableVibration(false)
        }

        // High-importance channel for the alarm trigger
        val alarmChannel = NotificationChannel(
            CHANNEL_ALARM,
            "Battery Alarm",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Fired when battery reaches the configured threshold"
        }

        nm.createNotificationChannel(serviceChannel)
        nm.createNotificationChannel(alarmChannel)
    }

    // ── Foreground service notification ──────────────────────────────────────

    /**
     * Builds the persistent silent notification shown while ChargingService runs.
     * Pass [batteryPct] to show the live percentage, and an optional [label] to
     * override the content text (e.g. during monthly soak countdown).
     */
    fun buildServiceNotification(
        batteryPct: Int,
        label: String? = null
    ): Notification {
        val contentText = label ?: "Charging — $batteryPct%"

        // Tapping the notification opens MainActivity
        val tapIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentTitle("Battery Alarm active — charging")
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(tapIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
            .also { notification ->
                notification.flags = notification.flags or
                        Notification.FLAG_ONGOING_EVENT or
                        Notification.FLAG_NO_CLEAR
            }
    }

    // ── Alarm notification (full-screen intent) ───────────────────────────────

    /**
     * Builds the high-priority alarm notification that launches [AlarmActivity]
     * via a full-screen intent so it fires even on the lock screen.
     */
    fun buildAlarmNotification(alarmMessage: String): Notification {
        val fullScreenIntent = Intent(context, AlarmActivity::class.java).apply {
            putExtra("alarm_message", alarmMessage)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            0,
            fullScreenIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(context, CHANNEL_ALARM)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("Battery Alert")
            .setContentText(alarmMessage)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setFullScreenIntent(fullScreenPendingIntent, true)
            .build()
    }
}