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
        if (!isScotiaPurchaseNotification(rawText, sbn.packageName)) return
        store.insertIfNew(sbn.packageName, rawText, sbn.postTime)
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

/**
 * Only capture actual purchase alerts, not every Scotia notification containing digits.
 * Bank account balances, card payments, codes and news must never become expenses.
 */
internal fun isScotiaPurchaseNotification(text: String, packageName: String): Boolean {
    val sourceIsScotia = packageName.contains("scotia", ignoreCase = true) ||
        text.contains("App Scotia", ignoreCase = true) ||
        text.contains("Scotiabank", ignoreCase = true)
    if (!sourceIsScotia) return false

    val purchase = Regex(
        """se\s+realiz[oó]\s+(?:una\s+)?compra\s+con\s+tu\s+tarjeta\s+de\s+(?:cr[eé]dito|d[eé]bito)""",
        RegexOption.IGNORE_CASE
    )
    val amountAndMerchant = Regex(
        """\bpor\s*\$\s*([0-9]{1,3}(?:\.[0-9]{3})+|[0-9]{4,})\s+en\s+([^·\s][^·]*?)(?:\s+Si\s+desconoces|\s*·|$)""",
        RegexOption.IGNORE_CASE
    )
    return purchase.containsMatchIn(text) && amountAndMerchant.containsMatchIn(text)
}
