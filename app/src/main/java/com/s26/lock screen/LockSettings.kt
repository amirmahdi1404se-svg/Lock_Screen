package com.s26.lockscreen

import android.content.Context

class LockSettings(context: Context) {
    private val prefs = context.getSharedPreferences("s26_lock_prefs", Context.MODE_PRIVATE)

    var isEnabled: Boolean
        get() = prefs.getBoolean("enabled", false)
        set(value) = prefs.edit().putBoolean("enabled", value).apply()

    var pinCode: String
        get() = prefs.getString("pin_code", "") ?: ""
        set(value) = prefs.edit().putString("pin_code", value).apply()

    var clockStyle: Int
        get() = prefs.getInt("clock_style", 0) // 0: Stacked OneUI, 1: Classic Bold, 2: Minimal Thin
        set(value) = prefs.edit().putInt("clock_style", value).apply()

    var wallpaperTheme: Int
        get() = prefs.getInt("wallpaper_theme", 0) // 0: S26 Titanium Blue, 1: Midnight Glass, 2: Aurora Dark
        set(value) = prefs.edit().putInt("wallpaper_theme", value).apply()

    var showNowBar: Boolean
        get() = prefs.getBoolean("now_bar", true)
        set(value) = prefs.edit().putBoolean("now_bar", value).apply()
}
