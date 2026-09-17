package com.vinaykaushik.batteryalarm

import androidx.activity.OnBackPressedCallback
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Bundle
import android.os.PowerManager
import android.view.WindowManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import com.vinaykaushik.batteryalarm.databinding.ActivityAlarmBinding

class AlarmActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAlarmBinding
    private lateinit var wakeLock: PowerManager.WakeLock
    private var mediaPlayer: MediaPlayer? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Wake the screen and show over lock screen
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )

        acquireWakeLock()

        binding = ActivityAlarmBinding.inflate(layoutInflater)
        setContentView(binding.root)

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                // Intentionally do nothing — must use DISMISS
            }
        })

        // Display the message passed from ChargingService
        val message = intent.getStringExtra("alarm_message")
            ?: "Battery alert!"
        binding.tvAlarmMessage.text = message

        startRingtone()

        registerReceiver(
            unplugReceiver,
            IntentFilter(Intent.ACTION_POWER_DISCONNECTED)
        )

        binding.btnDismiss.setOnClickListener {
            dismiss()
        }
    }

    private val unplugReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_POWER_DISCONNECTED) {
                dismiss()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // If alarm fires again while activity is showing, update message
        val message = intent.getStringExtra("alarm_message")
            ?: "Battery alert!"
        binding.tvAlarmMessage.text = message
    }

    override fun onDestroy() {
        super.onDestroy()
        releaseResources()
        try { unregisterReceiver(unplugReceiver) } catch (e: Exception) { }
    }

    // ── Alarm sound ──────────────────────────────────────────────────────────

    private fun startRingtone() {
        val prefs = PrefsManager.getInstance(this)
        val uri: Uri = resolveRingtoneUri(prefs.ringtoneUri)

        mediaPlayer = MediaPlayer().apply {
            try {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmActivity, uri)
                isLooping = true
                prepare()
                start()
            } catch (e: Exception) {
                // URI was broken — fall back to default alarm tone
                release()
                mediaPlayer = buildFallbackPlayer()
            }
        }
    }

    private fun resolveRingtoneUri(savedUri: String?): Uri {
        if (!savedUri.isNullOrBlank()) {
            return Uri.parse(savedUri)
        }
        return RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
    }

    private fun buildFallbackPlayer(): MediaPlayer? {
        val fallbackUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
            ?: return null
        return try {
            MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(this@AlarmActivity, fallbackUri)
                isLooping = true
                prepare()
                start()
            }
        } catch (e: Exception) {
            null  // Truly no sound available — silent fail
        }
    }

    // ── Wake lock ────────────────────────────────────────────────────────────

    private fun acquireWakeLock() {
        val pm = getSystemService(POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        wakeLock = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "BatteryAlarm::AlarmWakeLock"
        )
        wakeLock.acquire(10 * 60 * 1000L) // 10-minute max — user must dismiss
    }

    // ── Dismiss ──────────────────────────────────────────────────────────────

    private fun dismiss() {
        releaseResources()
        val nm = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        nm.cancel(NotificationHelper.NOTIF_ID_ALARM)
        finish()
    }


    private fun releaseResources() {
        mediaPlayer?.let {
            if (it.isPlaying) it.stop()
            it.release()
            mediaPlayer = null
        }
        if (::wakeLock.isInitialized && wakeLock.isHeld) {
            wakeLock.release()
        }
    }
}