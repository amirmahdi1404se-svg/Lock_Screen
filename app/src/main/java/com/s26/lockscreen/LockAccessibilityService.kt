package com.s26.lockscreen

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class LockAccessibilityService : AccessibilityService() {
    companion object {
        var instance: LockAccessibilityService? = null
        var isLockedNow: Boolean = false
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // در صورت تلاش برای باز کردن پنل اعلانات سیستم هنگام قفل بودن، آن را می‌بندد
        if (isLockedNow && event?.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val pkg = event.packageName?.toString() ?: ""
            if (pkg == "com.android.systemui") {
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
            }
        }
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }
}
