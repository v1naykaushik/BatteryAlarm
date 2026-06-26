package com.vinaykaushik.batteryalarm

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.app.NotificationManager
import android.provider.Settings
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.RingtoneManager
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.core.view.WindowCompat
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.vinaykaushik.batteryalarm.databinding.ActivityMainBinding
import android.graphics.Color
import android.view.View
import com.google.android.material.button.MaterialButtonToggleGroup

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var prefs: PrefsManager

    // ── Ringtone picker launcher ─────────────────────────────────────────────
    private val ringtoneLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri: Uri? = result.data
                ?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            if (uri != null) {
                prefs.ringtoneUri = uri.toString()
                updateRingtoneLabel(uri)
            }
        }
    }

    // ── Notification permission launcher (API 33+) ───────────────────────────
    private val notifPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(
                this,
                "Notifications permission denied — alarms may not appear",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // ── Lifecycle ────────────────────────────────────────────────────────────

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        WindowCompat.getInsetsController(window, window.decorView).isAppearanceLightStatusBars = true
        setSupportActionBar(binding.toolbar)

        if (Build.VERSION.SDK_INT >= 34) {
            val nm = getSystemService(NotificationManager::class.java)
            if (!nm.canUseFullScreenIntent()) {
                startActivity(
                    Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT)
                        .setData(Uri.parse("package:$packageName"))
                )
            }
        }
        prefs = PrefsManager.getInstance(this)
        requestNotificationPermissionIfNeeded()
        initControls()
    }

    override fun onResume() {
        super.onResume()
        refreshStatusCard()
        if (prefs.masterEnabled) {
            val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            val isCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0
            if (isCharging) {
                startForegroundService(Intent(this, ChargingService::class.java))
            }
            Log.d("BatteryAlarm", "MainActivity: onResume — masterEnabled=${prefs.masterEnabled} isCharging=$isCharging")
        }
    }

    // ── Permission ───────────────────────────────────────────────────────────

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this, Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    // ── Status card ──────────────────────────────────────────────────────────

    private fun refreshStatusCard() {
        val intentFilter = IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        val batteryIntent = registerReceiver(null, intentFilter)

        val level  = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale  = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val status = batteryIntent?.getIntExtra(
            BatteryManager.EXTRA_STATUS,
            BatteryManager.BATTERY_STATUS_UNKNOWN
        ) ?: BatteryManager.BATTERY_STATUS_UNKNOWN

        val pct = if (level >= 0) (level * 100 / scale) else 0
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        binding.tvBatteryPct.text      = "$pct%"
        binding.tvChargingStatus.text  = if (isCharging) "Charging" else "Not charging"

        // Monthly full charge badge
        val currentMonth   = java.time.YearMonth.now().let {
            "${it.year}-${it.monthValue.toString().padStart(2, '0')}"
        }
        val monthlyDone    = prefs.lastFullChargeMonth == currentMonth
        binding.ivMonthlyBadge.setImageResource(
            if (monthlyDone) android.R.drawable.presence_online   // green
            else             android.R.drawable.presence_away      // orange
        )
        binding.tvMonthlyStatus.text = if (monthlyDone)
            "Monthly charge done ✓" else "Monthly charge pending"

        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        val tempCelsius = if (tempRaw >= 0) tempRaw / 10 else -1

        if (prefs.isTempMode && tempCelsius >= 0) {
            binding.tvCurrentTemp.visibility = View.VISIBLE
            binding.tvCurrentTemp.text = "Current: ${tempCelsius}°C"
        } else {
            binding.tvCurrentTemp.visibility = View.GONE
        }
    }

    // ── Controls setup ───────────────────────────────────────────────────────

    private fun initControls() {

        // TEMP TEST BUTTON — delete after testing
//        binding.btnPickRingtone.setOnLongClickListener {
//            startForegroundService(Intent(this, ChargingService::class.java))
//            Toast.makeText(this, "Service started manually", Toast.LENGTH_SHORT).show()
//            true
//        }

        // ── Master switch ────────────────────────────────────────────────────
        binding.switchMaster.isChecked = prefs.masterEnabled
        binding.switchMaster.setOnCheckedChangeListener { _, isChecked ->
            prefs.masterEnabled = isChecked
            if (isChecked) {
                // Check if already charging right now
                val batteryStatus = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                val isCharging = batteryStatus?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1) != 0
                if (isCharging) {
                    startForegroundService(Intent(this, ChargingService::class.java))
                }
//                registerReceiver(
//                    ChargingReceiver(),
//                    IntentFilter(Intent.ACTION_POWER_CONNECTED)
//                )
            } else {
                stopService(Intent(this, ChargingService::class.java))
            }
        }

        // ── Threshold slider ─────────────────────────────────────────────────
        binding.sliderThreshold.apply {
            valueFrom = 50f
            valueTo   = 95f
            stepSize  = 1f
            value     = prefs.threshold.toFloat()
        }
        updateThresholdLabel(prefs.threshold)
        binding.sliderThreshold.addOnChangeListener { _, value, _ ->
            val pct = value.toInt()
            prefs.threshold = pct
            updateThresholdLabel(pct)
        }

        // ── Ringtone picker ──────────────────────────────────────────────────
        updateRingtoneLabel(prefs.ringtoneUri?.let { Uri.parse(it) })
        binding.btnPickRingtone.setOnClickListener {
            val intent = Intent(RingtoneManager.ACTION_RINGTONE_PICKER).apply {
                putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, true)
                putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT, false)
                val existing = prefs.ringtoneUri
                if (existing != null) {
                    putExtra(
                        RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                        Uri.parse(existing)
                    )
                }
            }
            ringtoneLauncher.launch(intent)
        }

        // ── Monthly charge switch ────────────────────────────────────────────
        binding.switchMonthly.isChecked = prefs.monthlyChargeEnabled
        binding.switchMonthly.setOnCheckedChangeListener { _, isChecked ->
            prefs.monthlyChargeEnabled = isChecked
        }

        // ── Alarm mode toggle ────────────────────────────────────────────────
        val isTempMode = prefs.isTempMode
        binding.toggleAlarmMode.check(
            if (isTempMode) R.id.btnModeTemp else R.id.btnModeBattery
        )
        binding.cardTempSlider.visibility = if (isTempMode) View.VISIBLE else View.GONE

        binding.toggleAlarmMode.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (!isChecked) return@addOnButtonCheckedListener
            val tempSelected = checkedId == R.id.btnModeTemp

            if (tempSelected) {
                val temp = readBatteryTempCelsius()
                if (temp == null) {
                    // Device doesn't provide temp — revert to battery mode
                    Toast.makeText(
                        this,
                        "Your device doesn't provide battery temperature information",
                        Toast.LENGTH_LONG
                    ).show()
                    binding.toggleAlarmMode.check(R.id.btnModeBattery)
                    // above check() triggers this listener again with btnModeBattery, so return here
                    return@addOnButtonCheckedListener
                }
            }

            prefs.isTempMode = tempSelected
            binding.cardTempSlider.visibility = if (tempSelected) View.VISIBLE else View.GONE
            refreshStatusCard()
        }

        // ── Temperature slider ───────────────────────────────────────────────
        binding.sliderTemp.apply {
            value = prefs.tempThreshold.toFloat()
            // Make track transparent so gradient view behind shows through
            trackActiveTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            trackInactiveTintList = android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
        }
        updateTempLabel(prefs.tempThreshold)
        binding.sliderTemp.addOnChangeListener { _, value, _ ->
            val temp = value.toInt()
            prefs.tempThreshold = temp
            updateTempLabel(temp)
        }
        refreshStatusCard()
    }

    // ── Label helpers ────────────────────────────────────────────────────────

    private fun updateThresholdLabel(pct: Int) {
        binding.tvThresholdLabel.text = "Alert at: $pct%"
    }

    private fun updateTempLabel(temp: Int) {
        binding.tvTempThresholdLabel.text = "Alert at: ${temp}°C"
    }

    private fun updateRingtoneLabel(uri: Uri?) {
        binding.tvRingtoneName.text = if (uri != null) {
            try {
                RingtoneManager.getRingtone(this, uri)?.getTitle(this)
                    ?: "Selected ringtone"
            } catch (e: Exception) {
                "Selected ringtone"
            }
        } else {
            "Default alarm tone"
        }
    }

    private fun readBatteryTempCelsius(): Int? {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val tempRaw = batteryIntent?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        // Some devices return 0 instead of -1 when temp is unavailable
        return if (tempRaw > 0) tempRaw / 10 else null
    }
}