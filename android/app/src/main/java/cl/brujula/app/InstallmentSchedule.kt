package cl.brujula.app

import java.util.Calendar

/**
 * A card purchase is one obligation for its full amount. Its monthly charges are projections,
 * never additional events; this prevents duplicate expenses when a statement arrives.
 * Month is zero-based, like Calendar.MONTH.
 */
internal fun installmentChargeForMonth(
    totalAmount: Int,
    installmentCount: Int,
    firstInstallmentMonth: String?,
    purchaseAt: Long,
    year: Int,
    month: Int
): Int {
    if (totalAmount <= 0 || month !in 0..11) return 0
    val purchase = Calendar.getInstance().apply { timeInMillis = purchaseAt }
    if (installmentCount <= 1) {
        return if (purchase.get(Calendar.YEAR) == year && purchase.get(Calendar.MONTH) == month) totalAmount else 0
    }
    val match = Regex("([0-9]{4})-([0-9]{2})").matchEntire(firstInstallmentMonth.orEmpty())
    val firstYear = match?.groupValues?.get(1)?.toIntOrNull() ?: purchase.get(Calendar.YEAR)
    val firstMonth = match?.groupValues?.get(2)?.toIntOrNull()?.minus(1) ?: purchase.get(Calendar.MONTH)
    if (firstMonth !in 0..11) return 0
    val offset = (year - firstYear) * 12 + month - firstMonth
    if (offset !in 0 until installmentCount) return 0
    val base = totalAmount / installmentCount
    val remainder = totalAmount % installmentCount
    return base + if (offset < remainder) 1 else 0
}

internal fun CapturedEvent.monthlyExpense(year: Int, month: Int): Int =
    if (movementType == "expense") installmentChargeForMonth(
        amount ?: 0, installmentCount, firstInstallmentMonth, receivedAt, year, month
    ) else 0
