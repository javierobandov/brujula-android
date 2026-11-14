package cl.brujula.app

import java.text.Normalizer
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal val CapturedEvent.isPending: Boolean get() = status == "captured" || status == "requires_review"
internal val CapturedEvent.isConfirmed: Boolean get() = status == "confirmed" || status == "reconciled"
internal val CapturedEvent.isLedgerEvent: Boolean get() = (isPending || isConfirmed) &&
    !(sourcePackage == "cl.brujula.app.statement" && status == "reconciled")

internal data class MonthlyBudget(
    val confirmedSpent: Int,
    val pendingReserved: Int,
    val confirmedRefunds: Int,
    val available: Int
)

/** Pending refunds cannot increase spendable funds. Confirmation merely moves a debit bucket. */
internal fun monthlyBudget(events: List<CapturedEvent>, income: Int, commitments: Int, goal: Int, year: Int, month: Int): MonthlyBudget {
    val ledger = events.filter { it.isLedgerEvent }
    val spent = ledger.filter { it.isConfirmed }.sumOf { it.monthlyExpense(year, month) }
    val pending = ledger.filter { it.isPending }.sumOf { it.monthlyExpense(year, month) }
    val refunds = ledger.filter { it.isConfirmed && it.movementType == "refund" }.filter {
        val date = Calendar.getInstance().apply { timeInMillis = it.receivedAt }
        date.get(Calendar.YEAR) == year && date.get(Calendar.MONTH) == month
    }.sumOf { it.amount ?: 0 }
    val net = (spent - refunds).coerceAtLeast(0)
    return MonthlyBudget(net, pending, refunds, income - commitments - goal - net - pending)
}

private fun searchable(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}"), "")

internal fun movementMatches(event: CapturedEvent, query: String, status: String, payment: String, day: String?): Boolean {
    if (!event.isLedgerEvent) return false
    if (status == "Pendientes" && !event.isPending) return false
    if (status == "Confirmados" && !event.isConfirmed) return false
    if (payment == "Tarjeta" && event.paymentSource != "tarjeta_credito") return false
    if (payment == "Cuenta" && event.paymentSource != "cuenta_corriente") return false
    if (!day.isNullOrBlank() && SimpleDateFormat("yyyy-MM-dd", Locale.US).format(event.receivedAt) != day) return false
    val needle = searchable(query.trim())
    if (needle.isEmpty()) return true
    val amount = event.amount?.toString().orEmpty()
    val formattedAmount = java.text.NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CL")).format(event.amount ?: 0)
    return listOf(event.merchant.orEmpty(), event.category, event.category.replace('_', ' '), event.rawText,
        amount, formattedAmount, SimpleDateFormat("dd-MM-yyyy", Locale.US).format(event.receivedAt)).any {
        searchable(it).contains(needle)
    } || (needle.matches(Regex("[\\d$.,\\s]+")) && needle.filter(Char::isDigit).let { it.isNotEmpty() && amount == it })
}

internal fun validMonth(value: String): Boolean = Regex("[0-9]{4}-(0[1-9]|1[0-2])").matches(value)

internal fun currentMonthKey(): String = SimpleDateFormat("yyyy-MM", Locale.US).format(java.util.Date())

/** Inclusive current installment: cuota 6 de 6 still belongs to this month. */
internal fun installmentNumber(firstMonth: String, year: Int, month: Int): Int {
    if (!validMonth(firstMonth)) return 0
    return (year - firstMonth.take(4).toInt()) * 12 + month - (firstMonth.takeLast(2).toInt() - 1) + 1
}
