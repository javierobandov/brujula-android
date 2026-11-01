package cl.brujula.app

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class InstallmentScheduleTest {
    private val purchaseAt = Calendar.getInstance().apply {
        clear()
        set(2026, Calendar.OCTOBER, 31, 12, 0, 0)
    }.timeInMillis

    @Test fun onePaymentUsesPurchaseMonth() {
        assertEquals(120_000, installmentChargeForMonth(120_000, 1, null, purchaseAt, 2026, Calendar.OCTOBER))
        assertEquals(0, installmentChargeForMonth(120_000, 1, null, purchaseAt, 2026, Calendar.NOVEMBER))
    }

    @Test fun dividesTotalAcrossFutureMonthsWithoutDoubleCounting() {
        val charges = (0..7).map { offset ->
            val month = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 1); add(Calendar.MONTH, offset) }
            installmentChargeForMonth(300_000, 6, "2026-11", purchaseAt, month.get(Calendar.YEAR), month.get(Calendar.MONTH))
        }
        assertEquals(listOf(50_000, 50_000, 50_000, 50_000, 50_000, 50_000, 0, 0), charges)
        assertEquals(300_000, charges.sum())
        assertEquals(0, installmentChargeForMonth(300_000, 6, "2026-11", purchaseAt, 2026, Calendar.OCTOBER))
    }

    @Test fun distributesRoundingAndCrossesYears() {
        val charges = listOf(
            installmentChargeForMonth(100, 3, "2026-12", purchaseAt, 2026, Calendar.DECEMBER),
            installmentChargeForMonth(100, 3, "2026-12", purchaseAt, 2027, Calendar.JANUARY),
            installmentChargeForMonth(100, 3, "2026-12", purchaseAt, 2027, Calendar.FEBRUARY)
        )
        assertEquals(listOf(34, 33, 33), charges)
        assertEquals(100, charges.sum())
        assertEquals(0, installmentChargeForMonth(100, 3, "2026-12", purchaseAt, 2027, Calendar.MARCH))
    }

    @Test fun changingCountOrStartMonthReprojectsWithoutAdditionalTransactions() {
        assertEquals(60_000, installmentChargeForMonth(120_000, 2, "2027-01", purchaseAt, 2027, Calendar.JANUARY))
        assertEquals(30_000, installmentChargeForMonth(120_000, 4, "2027-01", purchaseAt, 2027, Calendar.JANUARY))
        assertEquals(0, installmentChargeForMonth(120_000, 4, "2027-02", purchaseAt, 2027, Calendar.JANUARY))
    }
}
