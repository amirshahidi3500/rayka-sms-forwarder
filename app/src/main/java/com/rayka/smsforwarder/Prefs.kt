package com.rayka.smsforwarder

import android.content.Context
import android.content.SharedPreferences

object Prefs {
    private const val NAME = "rayka_prefs"
    private lateinit var sp: SharedPreferences

    private const val KEY_MAIN_URL = "main_url"
    private const val KEY_LOCAL_URL = "local_url"
    private const val KEY_INTERVAL = "interval_sec"
    private const val KEY_ONLINE_ENABLED = "online_enabled"
    private const val KEY_OFFLINE_ENABLED = "offline_enabled"
    private const val KEY_SERVICE_ENABLED = "service_enabled"
    private const val KEY_LAST_SMS_TS = "last_sms_ts"

    fun init(ctx: Context) {
        if (!::sp.isInitialized) {
            sp = ctx.applicationContext.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        }
    }

    var mainUrl: String
        get() = sp.getString(KEY_MAIN_URL, "") ?: ""
        set(v) = sp.edit().putString(KEY_MAIN_URL, v).apply()

    var localUrl: String
        get() = sp.getString(KEY_LOCAL_URL, "") ?: ""
        set(v) = sp.edit().putString(KEY_LOCAL_URL, v).apply()

    var intervalSeconds: Int
        get() = sp.getInt(KEY_INTERVAL, 15)
        set(v) = sp.edit().putInt(KEY_INTERVAL, v.coerceIn(3, 3600)).apply()

    var onlineEnabled: Boolean
        get() = sp.getBoolean(KEY_ONLINE_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_ONLINE_ENABLED, v).apply()

    var offlineEnabled: Boolean
        get() = sp.getBoolean(KEY_OFFLINE_ENABLED, true)
        set(v) = sp.edit().putBoolean(KEY_OFFLINE_ENABLED, v).apply()

    var serviceEnabled: Boolean
        get() = sp.getBoolean(KEY_SERVICE_ENABLED, false)
        set(v) = sp.edit().putBoolean(KEY_SERVICE_ENABLED, v).apply()

    // Timestamp (ms) of the newest SMS we have already processed/inserted.
    // Used at boot / cold start to catch up on messages received while the app was off.
    var lastSmsTimestamp: Long
        get() = sp.getLong(KEY_LAST_SMS_TS, 0L)
        set(v) = sp.edit().putLong(KEY_LAST_SMS_TS, v).apply()
}
