package com.s26.lockscreen

import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {

    private lateinit var settings: LockSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        settings = LockSettings(this)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#0A0D14"))
        }

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 64, 48, 64)
        }

        // هدر برنامه
        val title = TextView(this).apply {
            text = "Galaxy S26 Lock Screen"
            setTextColor(Color.WHITE)
            textSize = 26f
            typeface = Typeface.DEFAULT_BOLD
        }
        val subtitle = TextView(this).apply {
            text = "شخصی‌سازی کامل One UI بدون اثر انگشت"
            setTextColor(Color.parseColor("#94A3B8"))
            textSize = 14f
            setPadding(0, 8, 0, 40)
        }
        root.addView(title)
        root.addView(subtitle)

        // کارت ۱: فعال‌سازی اصلی و پیش‌نمایش زنده
        val mainCard = createGlassCard()
        val enableSwitch = Switch(this).apply {
            text = "فعال‌سازی لاک‌اسکرین S26"
            setTextColor(Color.WHITE)
            textSize = 16f
            isChecked = settings.isEnabled
            setOnCheckedChangeListener { _, isChecked ->
                if (isChecked && !Settings.canDrawOverlays(this@MainActivity)) {
                    isChecked = false
                    Toast.makeText(this@MainActivity, "ابتدا مجوز نمایش روی برنامه‌ها را بدهید", Toast.LENGTH_SHORT).show()
                    return@setOnCheckedChangeListener
                }
                settings.isEnabled = isChecked
                val intent = Intent(this@MainActivity, LockScreenService::class.java)
                if (isChecked) {
                    ContextCompat.startForegroundService(this@MainActivity, intent)
                } else {
                    stopService(intent)
                }
            }
        }
        mainCard.addView(enableSwitch)

        val previewBtn = createActionButton("👁️ پیش‌نمایش زنده لاک‌اسکرین S26", "#2563EB") {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "لطفاً مجوز نمایش روی برنامه‌ها را فعال کنید", Toast.LENGTH_SHORT).show()
            } else {
                val intent = Intent(this, LockScreenService::class.java).apply {
                    action = "PREVIEW_LOCK"
                }
                ContextCompat.startForegroundService(this, intent)
            }
        }
        mainCard.addView(previewBtn)
        root.addView(mainCard)

        // کارت ۲: مجوزهای ضروری اندروید
        val permCard = createGlassCard()
        permCard.addView(createSectionHeader("مجوزهای ضروری"))

        permCard.addView(createActionButton("۱. مجوز نمایش روی برنامه‌های دیگر (Overlay)", "#1E293B") {
            startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        })
        permCard.addView(createActionButton("۲. مجوز دسترسی به اعلان‌ها (Notifications)", "#1E293B") {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        })
        permCard.addView(createActionButton("۳. سرویس دسترسی‌پذیری (قفل دکمه‌ها)", "#1E293B") {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        })
        root.addView(permCard)

        // کارت ۳: امنیت (پین یا سوایپ)
        val securityCard = createGlassCard()
        securityCard.addView(createSectionHeader("روش باز کردن قفل (بدون اثر انگشت)"))

        val pinInput = EditText(this).apply {
            hint = if (settings.pinCode.isEmpty()) "خالی = باز شدن با کشیدن (Swipe)" else "پین فعلی تنظیم شده است"
            setHintTextColor(Color.parseColor("#64748B"))
            setTextColor(Color.WHITE)
            inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD
            setPadding(32, 24, 32, 24)
            background = createGlassDrawable("#161D2C", 24f)
        }
        securityCard.addView(pinInput)

        val savePinBtn = createActionButton("ذخیره پین (برای حذف پین خالی بگذارید)", "#0F766E") {
            val newPin = pinInput.text.toString().trim()
            settings.pinCode = newPin
            val msg = if (newPin.isEmpty()) "قفل روی حالت Swipe تنظیم شد" else "پین جدید با موفقیت ذخیره شد"
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
        }
        securityCard.addView(savePinBtn)
        root.addView(securityCard)

        // کارت ۴: شخصی‌سازی ظاهر S26
        val styleCard = createGlassCard()
        styleCard.addView(createSectionHeader("شخصی‌سازی ظاهر و Now Bar"))

        val nowBarSwitch = Switch(this).apply {
            text = "نمایش نوار زنده پایین صفحه (Now Bar)"
            setTextColor(Color.WHITE)
            textSize = 15f
            isChecked = settings.showNowBar
            setPadding(0, 12, 0, 20)
            setOnCheckedChangeListener { _, isChecked ->
                settings.showNowBar = isChecked
            }
        }
        styleCard.addView(nowBarSwitch)

        val clockStyles = arrayOf("ساعت پیش‌فرض One UI", "ساعت ضخیم S26", "ساعت مینیمال ظریف")
        val clockBtn = createActionButton("تغییر استایل ساعت: ${clockStyles[settings.clockStyle]}", "#1E293B") { button ->
            settings.clockStyle = (settings.clockStyle + 1) % 3
            button.text = "تغییر استایل ساعت: ${clockStyles[settings.clockStyle]}"
        }
        styleCard.addView(clockBtn)

        val themes = arrayOf("آبی تیتانیوم S26", "شیشه نیمه‌شب", "شفق قطبی تیره")
        val themeBtn = createActionButton("تغییر پس‌زمینه: ${themes[settings.wallpaperTheme]}", "#1E293B") { button ->
            settings.wallpaperTheme = (settings.wallpaperTheme + 1) % 3
            button.text = "تغییر پس‌زمینه: ${themes[settings.wallpaperTheme]}"
        }
        styleCard.addView(themeBtn)
        root.addView(styleCard)

        scroll.addView(root)
        setContentView(scroll)
    }

    private fun createGlassCard(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(36, 36, 36, 36)
            background = createGlassDrawable("#131824", 36f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 0, 0, 32)
            }
        }
    }

    private fun createSectionHeader(textStr: String): TextView {
        return TextView(this).apply {
            text = textStr
            setTextColor(Color.parseColor("#38BDF8"))
            textSize = 15f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(0, 0, 0, 20)
        }
    }

    private fun createActionButton(label: String, hexColor: String, onClick: (Button) -> Unit): Button {
        return Button(this).apply {
            text = label
            setTextColor(Color.WHITE)
            isAllCaps = false
            gravity = Gravity.CENTER
            background = createGlassDrawable(hexColor, 24f)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                setMargins(0, 16, 0, 0)
            }
            setOnClickListener { onClick(this) }
        }
    }

    private fun createGlassDrawable(hexColor: String, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            setColor(Color.parseColor(hexColor))
            setStroke(2, Color.parseColor("#26FFFFFF"))
            cornerRadius = radius
        }
    }
}
