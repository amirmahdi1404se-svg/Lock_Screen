package com.s26.lockscreen

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.WindowManager
import androidx.core.app.NotificationCompat

class LockScreenService : Service() {

    private lateinit var windowManager: WindowManager
    private var lockView: LockScreenView? = null
    private lateinit var settings: LockSettings

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    if (settings.isEnabled) showLockScreen()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        settings = LockSettings(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        startForegroundNotification()

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "PREVIEW_LOCK") {
            showLockScreen()
        }
        return START_STICKY
    }

    fun showLockScreen() {
        if (!Settings.canDrawOverlays(this)) return
        if (lockView != null) return

        val view = LockScreenView(this, settings) {
            removeLockScreen()
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_FULLSCREEN or
                    WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD,
            PixelFormat.TRANSLUCENT
        )

        try {
            windowManager.addView(view, params)
            lockView = view
            LockAccessibilityService.isLockedNow = true
        } catch (_: Exception) {
        }
    }

    private fun removeLockScreen() {
        lockView?.let {
            try {
                windowManager.removeView(it)
            } catch (_: Exception) {
            }
        }
        lockView = null
        LockAccessibilityService.isLockedNow = false
    }

    private fun startForegroundNotification() {
        val channelId = "s26_lock_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Galaxy S26 Lock Screen Service",
                NotificationManager.IMPORTANCE_LOW
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle("لاک‌اسکرین Galaxy S26 فعال است")
            .setContentText("در حال اجرا در پس‌زمینه")
            .setSmallIcon(android.R.drawable.ic_lock_idle_lock)
            .setOngoing(true)
            .build()

        startForeground(101, notification)
    }

    override fun onDestroy() {
        unregisterReceiver(screenReceiver)
        removeLockScreen()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
