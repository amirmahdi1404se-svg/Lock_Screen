package com.s26.lockscreen

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

data class LockNotifItem(
    val key: String,
    val appName: String,
    val title: String,
    val text: String
)

class S26NotificationListener : NotificationListenerService() {
    companion object {
        val activeNotificationsList = mutableListOf<LockNotifItem>()
        var onNotificationsUpdated: (() -> Unit)? = null
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        refreshList()
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        refreshList()
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        refreshList()
    }

    private fun refreshList() {
        try {
            val current = activeNotifications ?: return
            activeNotificationsList.clear()
            for (sbn in current) {
                if (sbn.isOngoing) continue
                val extras = sbn.notification.extras
                val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: continue
                val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString() ?: ""
                val appLabel = try {
                    val info = packageManager.getApplicationInfo(sbn.packageName, 0)
                    packageManager.getApplicationLabel(info).toString()
                } catch (e: Exception) {
                    sbn.packageName.substringAfterLast('.')
                }
                activeNotificationsList.add(LockNotifItem(sbn.key, appLabel, title, text))
                if (activeNotificationsList.size >= 4) break
            }
            onNotificationsUpdated?.invoke()
        } catch (_: Exception) {
        }
    }
}
