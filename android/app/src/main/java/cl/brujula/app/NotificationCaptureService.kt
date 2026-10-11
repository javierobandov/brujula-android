package cl.brujula.app

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification

class NotificationCaptureService : NotificationListenerService() {
    private lateinit var store: CapturedEventStore

    override fun onCreate() {
        super.onCreate()
        store = CapturedEventStore(this)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        val rawText = notificationText(sbn.notification)
        if (rawText.isBlank() || !looksFinancial(rawText, sbn.packageName)) return
        store.insertIfNew(sbn.packageName, rawText, sbn.postTime)
    }

    private fun looksFinancial(text: String, packageName: String): Boolean {
        val haystack = "$packageName $text".lowercase()
        val bankSignal = listOf("scotia", "scotiabank", "app scotia")
        val moneySignal = Regex("\\$?\\s*[0-9]{1,3}(?:[.][0-9]{3})+|\\$?\\s*[0-9]{4,}")
        return bankSignal.any(haystack::contains) && moneySignal.containsMatchIn(text)
    }

    private fun notificationText(notification: Notification): String {
        val extras = notification.extras ?: return ""
        val parts = mutableListOf<String>()
        extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.let { parts.add(it) }
        extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let { parts.add(it) }
        extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let { parts.add(it) }
        extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.forEach { line -> parts.add(line.toString()) }
        return parts.map { it.trim() }.filter { it.isNotEmpty() }.distinct().joinToString(" · ")
    }
}
