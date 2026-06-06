package com.vinaykaushik.batteryalarm

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import java.time.YearMonth

class ChargingService : Service() {

    private lateinit var prefs: PrefsManager
    private lateinit var notifHelper: NotificationHelper
    private lateinit var wakeLock: PowerManager.WakeLock
    private var soakStartTimeMs = 0L

    private val handler = Handler(Looper.getMainLooper())

    // Per-session flags
    private var alarmFiredThisSession = false
    private var isMonthlyChargeSession = false
    private var monthlySoakScheduled = false
    private var currentBatteryPct = 0

    private val soakRunnable = Runnable {
        // 30-minute soak complete
        prefs.lastFullChargeMonth = currentMonthString()
        launchAlarm("Monthly soak complete. Please unplug and restart your phone.")
    }

    // ── Battery receiver ─────────────────────────────────────────────────────

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val level  = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
            val scale  = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100)
            val status = intent.getIntExtra(
                BatteryManager.EXTRA_STATUS,
                BatteryManager.BATTERY_STATUS_UNKNOWN
            )

            if (level < 0) return

            currentBatteryPct = (level * 100 / scale)
            val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                    status == BatteryManager.BATTERY_STATUS_FULL

            // Update foreground notification
            updateServiceNotification()
            Log.d("BatteryAlarm", "ChargingService: battery update — pct=$currentBatteryPct isCharging=$isCharging")
            if (!isCharging) {
                stopSelf()
                return
            }

            handleBatteryLevel(currentBatteryPct)
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate() {
        super.onCreate()
        prefs       = PrefsManager.getInstance(this)
        notifHelper = NotificationHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d("BatteryAlarm", "ChargingService: onStartCommand called — masterEnabled = ${prefs.masterEnabled}")
        // Guard: if master toggle is off, shut down immediately
        if (!prefs.masterEnabled) {
            stopSelf()
            return START_NOT_STICKY
        }

        // Promote to foreground immediately
        startForeground(
            NotificationHelper.NOTIF_ID_SERVICE,
            notifHelper.buildServiceNotification(currentBatteryPct)
        )
        Log.d("BatteryAlarm", "ChargingService: startForeground called successfully")
        // Now safe to do everything else
        acquireWakeLock()
        registerBatteryReceiver()
        Log.d("BatteryAlarm", "ChargingService: battery receiver registered")
        // Reset per-session state
        alarmFiredThisSession = false
        monthlySoakScheduled  = false
        handler.removeCallbacks(soakRunnable)

        // Determine if this is the monthly full-charge session
        isMonthlyChargeSession = prefs.monthlyChargeEnabled &&
                prefs.lastFullChargeMonth != currentMonthString()

        return START_STICKY
    }

    override fun onDestroy() {
        Log.d("BatteryAlarm", "ChargingService: onDestroy called — service is stopping")
        super.onDestroy()
        handler.removeCallbacks(soakRunnable)
        try { unregisterReceiver(batteryReceiver) } catch (e: Exception) { }
        if (::wakeLock.isInitialized && wakeLock.isHeld) wakeLock.release()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── Core logic ───────────────────────────────────────────────────────────

    private fun handleBatteryLevel(pct: Int) {
        // Re-read monthly toggle live in case it changed
        val monthlyEnabled = prefs.monthlyChargeEnabled

        if (isMonthlyChargeSession && monthlyEnabled) {
            handleMonthlySession(pct)
        } else {
            // If monthly was on but just got toggled off mid-soak, cancel soak
            if (monthlySoakScheduled) {
                handler.removeCallbacks(soakRunnable)
                monthlySoakScheduled  = false
                isMonthlyChargeSession = false
            }
            handleNormalThreshold(pct)
        }
    }

    private fun handleNormalThreshold(pct: Int) {
        Log.d("BatteryAlarm", "ChargingService: threshold reached — pct=$pct threshold=${prefs.threshold}")
        if (alarmFiredThisSession) return

        val threshold = prefs.threshold   // always read live
        if (pct >= threshold) {
            alarmFiredThisSession = true
            launchAlarm("Battery sufficiently charged.")
        }
    }

    private fun handleMonthlySession(pct: Int) {
        if (pct >= 100 && !monthlySoakScheduled) {
            monthlySoakScheduled = true
            soakStartTimeMs = System.currentTimeMillis()
            updateServiceNotification(soakMinutesRemaining = 30)
            handler.postDelayed(soakRunnable, 30 * 60 * 1000L)
        }

        // Update soak countdown label in notification (approximate minutes)
        if (monthlySoakScheduled) {
            updateServiceNotification()
        }
    }

    // ── Notification helpers ─────────────────────────────────────────────────

    private fun updateServiceNotification(soakMinutesRemaining: Int? = null) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager

        val label = when {
            monthlySoakScheduled -> {
                val elapsedMs = System.currentTimeMillis() - soakStartTimeMs
                val remainingMins = 30 - (elapsedMs / 60000).toInt()
                "Monthly charge — soaking: $remainingMins min remaining"
            }
            isMonthlyChargeSession -> "Monthly full charge in progress — $currentBatteryPct%"
            else -> null   // buildServiceNotification will use default "Charging — XX%"
        }

        nm.notify(
            NotificationHelper.NOTIF_ID_SERVICE,
            notifHelper.buildServiceNotification(currentBatteryPct, label)
        )
    }

    // ── Alarm launcher ───────────────────────────────────────────────────────

    private fun launchAlarm(message: String) {
        // Post alarm notification (triggers full-screen intent)
        val nm = getSystemService(Context.NOTIFICATION_SERVICE)
                as android.app.NotificationManager
        nm.notify(
            NotificationHelper.NOTIF_ID_ALARM,
            notifHelper.buildAlarmNotification(message)
        )

        // Also start AlarmActivity directly for reliability
        val alarmIntent = Intent(this, AlarmActivity::class.java).apply {
            putExtra("alarm_message", message)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(alarmIntent)
    }

    // ── Setup helpers ────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "BatteryAlarm::ChargingWakeLock"
        )
        wakeLock.acquire(10 * 60 * 60 * 1000L) // 10-hour max safety timeout
    }

    private fun registerBatteryReceiver() {
        val filter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        registerReceiver(batteryReceiver, filter)
    }

    private fun currentMonthString(): String {
        val ym = YearMonth.now()
        return "${ym.year}-${ym.monthValue.toString().padStart(2, '0')}"
    }
}