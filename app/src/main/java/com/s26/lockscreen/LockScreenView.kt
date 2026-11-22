package com.s26.lockscreen

import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.os.BatteryManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.MediaStore
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class LockScreenView(
    context: Context,
    private val settings: LockSettings,
    private val onUnlock: () -> Unit
) : FrameLayout(context) {

    private val handler = Handler(Looper.getMainLooper())
    private var isTorchOn = false
    private var enteredPin = ""
    private var startY = 0f

    private val mainContainer: LinearLayout
    private val pinOverlay: LinearLayout
    private val pinDotsText: TextView
    private val notifContainer: LinearLayout
    private val clockHourText: TextView
    private val clockMinText: TextView
    private val dateBatteryText: TextView
    private val nowBarStatusText: TextView

    private val timeTicker = object : Runnable {
        override fun run() {
            updateClockAndWidgets()
            handler.postDelayed(this, 1000L)
        }
    }

    init {
        applyS26Wallpaper()

        // لایه اصلی لاک‌اسکرین One UI
        mainContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(48, 120, 48, 64)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }

        // نوار وضعیت بالای صفحه (نام اپراتور و آیکون قفل)
        val topStatus = TextView(context).apply {
            text = "🔒  Galaxy S26  •  One UI"
            setTextColor(Color.parseColor("#B0FFFFFF"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 40)
        }
        mainContainer.addView(topStatus)

        // ساعت بزرگ S26
        clockHourText = TextView(context).apply {
            setTextColor(Color.WHITE)
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        clockMinText = TextView(context).apply {
            setTextColor(Color.parseColor("#DCE6FF"))
            gravity = Gravity.CENTER
            includeFontPadding = false
        }
        applyClockStyle()
        mainContainer.addView(clockHourText)
        mainContainer.addView(clockMinText)

        // ویجت تاریخ و باتری زیر ساعت
        dateBatteryText = TextView(context).apply {
            setTextColor(Color.parseColor("#E6FFFFFF"))
            textSize = 15f
            gravity = Gravity.CENTER
            setPadding(0, 20, 0, 36)
        }
        mainContainer.addView(dateBatteryText)

        // بخش کارت‌های اعلان شیشه‌ای (Notifications)
        notifContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        }
        mainContainer.addView(notifContainer)

        // راهنمای کشیدن برای باز کردن قفل
        val swipeHint = TextView(context).apply {
            text = if (settings.pinCode.isNotEmpty()) "برای وارد کردن پین به بالا بکشید" else "برای باز کردن قفل به بالا بکشید"
            setTextColor(Color.parseColor("#99FFFFFF"))
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(0, 12, 0, 24)
        }
        mainContainer.addView(swipeHint)

        // ردیف پایین: چراغ قوه + Now Bar زنده + دوربین
        val bottomRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        val torchBtn = createCircleShortcut("🔦") {
            toggleFlashlight()
            vibrateLight()
        }

        // نوار زنده S26 (Now Bar)
        val nowBarPill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(28, 18, 28, 18)
            background = createGlassPill(Color.parseColor("#38151B28"), Color.parseColor("#4DFFFFFF"), 999f)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                setMargins(24, 0, 24, 0)
            }
            visibility = if (settings.showNowBar) View.VISIBLE else View.INVISIBLE
        }

        val prevBtn = createMediaMiniBtn("⏮") { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS) }
        nowBarStatusText = TextView(context).apply {
            text = "Now Bar • در حال آماده\u200cباش"
            setTextColor(Color.WHITE)
            textSize = 12f
            maxLines = 1
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val playPauseBtn = createMediaMiniBtn("⏯") { sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) }
        val nextBtn = createMediaMiniBtn("⏭") { sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT) }

        nowBarPill.addView(prevBtn)
        nowBarPill.addView(nowBarStatusText)
        nowBarPill.addView(playPauseBtn)
        nowBarPill.addView(nextBtn)

        val cameraBtn = createCircleShortcut("📷") {
            vibrateLight()
            openCameraAndUnlock()
        }

        bottomRow.addView(torchBtn)
        bottomRow.addView(nowBarPill)
        bottomRow.addView(cameraBtn)
        mainContainer.addView(bottomRow)

        addView(mainContainer)

        // لایه صفحه‌کلید پین (مخفی تا زمان کشیدن به بالا)
        pinDotsText = TextView(context).apply {
            text = "پین خود را وارد کنید"
            setTextColor(Color.WHITE)
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, 36)
        }
        pinOverlay = buildPinOverlay()
        pinOverlay.visibility = View.GONE
        addView(pinOverlay)

        S26NotificationListener.onNotificationsUpdated = {
            handler.post { renderNotifications() }
        }
        renderNotifications()
        handler.post(timeTicker)
    }

    private fun applyS26Wallpaper() {
        val colors = when (settings.wallpaperTheme) {
            1 -> intArrayOf(Color.parseColor("#08090D"), Color.parseColor("#141926"), Color.parseColor("#0A0C12"))
            2 -> intArrayOf(Color.parseColor("#0B192C"), Color.parseColor("#1E3E62"), Color.parseColor("#090D16"))
            else -> intArrayOf(Color.parseColor("#0F172A"), Color.parseColor("#1E293B"), Color.parseColor("#3B82F6"))
        }
        background = GradientDrawable(GradientDrawable.Orientation.TL_BR, colors)
    }

    private fun applyClockStyle() {
        when (settings.clockStyle) {
            1 -> {
                clockHourText.textSize = 72f
                clockMinText.textSize = 72f
                clockHourText.typeface = Typeface.DEFAULT_BOLD
                clockMinText.typeface = Typeface.DEFAULT_BOLD
            }
            2 -> {
                clockHourText.textSize = 64f
                clockMinText.textSize = 64f
                clockHourText.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                clockMinText.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            }
            else -> {
                clockHourText.textSize = 84f
                clockMinText.textSize = 84f
                clockHourText.typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
                clockMinText.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            }
        }
    }

    private fun updateClockAndWidgets() {
        val now = Date()
        clockHourText.text = SimpleDateFormat("HH", Locale.getDefault()).format(now)
        clockMinText.text = SimpleDateFormat("mm", Locale.getDefault()).format(now)

        val dateStr = SimpleDateFormat("EEEE، d MMMM", Locale("fa")).format(now)
        val batteryIntent = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        val bolt = if (isCharging) "⚡ در حال شارژ" else "🔋"

        dateBatteryText.text = "$dateStr   •   $bolt $level%"
        if (isCharging) {
            nowBarStatusText.text = "⚡ شارژ سریع S26 ($level%)"
        } else {
            nowBarStatusText.text = "Now Bar • کنترل رسانه"
        }
    }

    private fun renderNotifications() {
        notifContainer.removeAllViews()
        val list = S26NotificationListener.activeNotificationsList
        for (item in list) {
            val card = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 22, 32, 22)
                background = createGlassPill(Color.parseColor("#33FFFFFF"), Color.parseColor("#3DFFFFFF"), 42f)
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(0, 0, 0, 16)
                }
            }
            val header = TextView(context).apply {
                text = "${item.appName}  •  ${item.title}"
                setTextColor(Color.WHITE)
                textSize = 13f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 1
            }
            val body = TextView(context).apply {
                text = item.text
                setTextColor(Color.parseColor("#D9FFFFFF"))
                textSize = 12f
                maxLines = 2
            }
            card.addView(header)
            if (item.text.isNotEmpty()) card.addView(body)
            notifContainer.addView(card)
        }
    }

    private fun buildPinOverlay(): LinearLayout {
        val overlay = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#D9080B12"))
            setPadding(64, 64, 64, 64)
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        }
        overlay.addView(pinDotsText)

        val rows = listOf(
            listOf("1", "2", "3"),
            listOf("4", "5", "6"),
            listOf("7", "8", "9"),
            listOf("بازگشت", "0", "⌫")
        )

        for (row in rows) {
            val rowLayout = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER
            }
            for (key in row) {
                val btn = TextView(context).apply {
                    text = key
                    setTextColor(Color.WHITE)
                    textSize = if (key.length > 1) 14f else 24f
                    gravity = Gravity.CENTER
                    background = createGlassPill(Color.parseColor("#26FFFFFF"), Color.parseColor("#40FFFFFF"), 999f)
                    layoutParams = LinearLayout.LayoutParams(170, 170).apply {
                        setMargins(18, 18, 18, 18)
                    }
                    setOnClickListener {
                        vibrateLight()
                        handlePinKey(key)
                    }
                }
                rowLayout.addView(btn)
            }
            overlay.addView(rowLayout)
        }
        return overlay
    }

    private fun handlePinKey(key: String) {
        when (key) {
            "بازگشت" -> {
                enteredPin = ""
                pinDotsText.text = "پین خود را وارد کنید"
                pinOverlay.visibility = View.GONE
            }
            "⌫" -> {
                if (enteredPin.isNotEmpty()) {
                    enteredPin = enteredPin.dropLast(1)
                    pinDotsText.text = "● ".repeat(enteredPin.length).ifEmpty { "پین خود را وارد کنید" }
                }
            }
            else -> {
                enteredPin += key
                pinDotsText.text = "● ".repeat(enteredPin.length)
                if (enteredPin == settings.pinCode) {
                    cleanupAndUnlock()
                } else if (enteredPin.length >= settings.pinCode.length) {
                    enteredPin = ""
                    pinDotsText.text = "پین اشتباه است، دوباره تلاش کنید"
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (pinOverlay.visibility == View.VISIBLE) return super.onTouchEvent(event)
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                startY = event.y
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaY = event.y - startY
                if (deltaY < 0) {
                    mainContainer.translationY = deltaY * 0.45f
                    mainContainer.alpha = (1f - (abs(deltaY) / 900f)).coerceIn(0.3f, 1f)
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val deltaY = event.y - startY
                mainContainer.animate().translationY(0f).alpha(1f).setDuration(200).start()
                if (deltaY < -220f) {
                    vibrateLight()
                    if (settings.pinCode.isEmpty()) {
                        cleanupAndUnlock()
                    } else {
                        pinOverlay.visibility = View.VISIBLE
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun createCircleShortcut(icon: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = icon
            textSize = 20f
            gravity = Gravity.CENTER
            background = createGlassPill(Color.parseColor("#33FFFFFF"), Color.parseColor("#4DFFFFFF"), 999f)
            layoutParams = LinearLayout.LayoutParams(116, 116)
            setOnClickListener { onClick() }
        }
    }

    private fun createMediaMiniBtn(symbol: String, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            text = symbol
            setTextColor(Color.WHITE)
            textSize = 15f
            setPadding(16, 8, 16, 8)
            setOnClickListener {
                vibrateLight()
                onClick()
            }
        }
    }

    private fun createGlassPill(fillColor: Int, strokeColor: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(fillColor)
            setStroke(2, strokeColor)
            cornerRadius = radius
        }
    }

    private fun toggleFlashlight() {
        try {
            val camManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
            val cameraId = camManager.cameraIdList[0]
            isTorchOn = !isTorchOn
            camManager.setTorchMode(cameraId, isTorchOn)
        } catch (_: Exception) {
        }
    }

    private fun openCameraAndUnlock() {
        try {
            val camIntent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(camIntent)
            cleanupAndUnlock()
        } catch (_: Exception) {
        }
    }

    private fun sendMediaKey(keyCode: Int) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, keyCode))
        audioManager.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, keyCode))
    }

    private fun vibrateLight() {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(25)
        }
    }

    private fun cleanupAndUnlock() {
        handler.removeCallbacks(timeTicker)
        S26NotificationListener.onNotificationsUpdated = null
        onUnlock()
    }
}
