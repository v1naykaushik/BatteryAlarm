package com.vinaykaushik.batteryalarm

import android.content.Context
import android.content.SharedPreferences

class PrefsManager private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Master toggle ────────────────────────────────────────────────────────
    var masterEnabled: Boolean
        get() = prefs.getBoolean(KEY_MASTER_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MASTER_ENABLED, value).apply()

    // ── Threshold (50–95, default 81) ────────────────────────────────────────
    var threshold: Int
        get() = prefs.getInt(KEY_THRESHOLD, 81)
        set(value) = prefs.edit().putInt(KEY_THRESHOLD, value).apply()

    // ── Ringtone URI (nullable) ──────────────────────────────────────────────
    var ringtoneUri: String?
        get() = prefs.getString(KEY_RINGTONE_URI, null)
        set(value) = prefs.edit().putString(KEY_RINGTONE_URI, value).apply()

    // ── Monthly full-charge feature (default on) ─────────────────────────────
    var monthlyChargeEnabled: Boolean
        get() = prefs.getBoolean(KEY_MONTHLY_CHARGE_ENABLED, true)
        set(value) = prefs.edit().putBoolean(KEY_MONTHLY_CHARGE_ENABLED, value).apply()

    // ── Last month a full charge was completed (e.g. "2025-03") ─────────────
    var lastFullChargeMonth: String
        get() = prefs.getString(KEY_LAST_FULL_CHARGE_MONTH, "") ?: ""
        set(value) = prefs.edit().putString(KEY_LAST_FULL_CHARGE_MONTH, value).apply()

    // ── Alarm mode: false = battery, true = temperature ──────────────────────
    var isTempMode: Boolean
        get() = prefs.getBoolean(KEY_IS_TEMP_MODE, false)
        set(value) = prefs.edit().putBoolean(KEY_IS_TEMP_MODE, value).apply()

    // ── Temperature threshold (25–55°C, default 40) ──────────────────────────
    var tempThreshold: Int
        get() = prefs.getInt(KEY_TEMP_THRESHOLD, 40)
        set(value) = prefs.edit().putInt(KEY_TEMP_THRESHOLD, value).apply()

    // ── Singleton plumbing ───────────────────────────────────────────────────
    companion object {
        private const val PREFS_NAME = "battery_alarm_prefs"

        private const val KEY_MASTER_ENABLED         = "master_enabled"
        private const val KEY_THRESHOLD              = "threshold"
        private const val KEY_RINGTONE_URI           = "ringtone_uri"
        private const val KEY_MONTHLY_CHARGE_ENABLED = "monthly_charge_enabled"
        private const val KEY_LAST_FULL_CHARGE_MONTH = "last_full_charge_month"
        private const val KEY_IS_TEMP_MODE    = "is_temp_mode"
        private const val KEY_TEMP_THRESHOLD  = "temp_threshold"

        @Volatile
        private var INSTANCE: PrefsManager? = null

        fun getInstance(context: Context): PrefsManager =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: PrefsManager(context).also { INSTANCE = it }
            }
    }
}