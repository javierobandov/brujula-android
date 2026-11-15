package cl.brujula.app

import org.junit.Assert.*
import org.junit.Test
import java.util.Calendar

class MonthlyBudgetTest {
    private val timestamp = Calendar.getInstance().apply { clear(); set(2026, Calendar.NOVEMBER, 14, 12, 0) }.timeInMillis
    private fun event(status: String = "captured", amount: Int? = 12_000, type: String = "expense") =
        CapturedEvent(1, "test", timestamp, "bank", "Pago cafetería", amount, "Café", type, "alimentacion", "tarjeta_credito", status)
    private fun budget(events: List<CapturedEvent>) = monthlyBudget(events, 100_000, 20_000, 10_000, 2026, Calendar.NOVEMBER)

    @Test fun confirmingPendingMovesReservationWithoutSubtractingAgain() {
        val pending = budget(listOf(event()))
        val confirmed = budget(listOf(event("confirmed")))
        assertEquals(58_000, pending.available)
        assertEquals(pending.available, confirmed.available)
        assertEquals(12_000, pending.pendingReserved)
        assertEquals(0, pending.confirmedSpent)
        assertEquals(12_000, confirmed.confirmedSpent)
        assertEquals(0, confirmed.pendingReserved)
    }

    @Test fun statementEvidenceIgnoredAndDemoDoNotDuplicateBudget() {
        val source = event("confirmed")
        val all = listOf(source, source.copy(id=2, sourcePackage="cl.brujula.app.statement", status="reconciled"), event("ignored"), event("demo"))
        assertEquals(budget(listOf(source)), budget(all))
    }

    @Test fun pendingRefundTransfersAndCardPaymentCannotIncreaseSpendableFunds() {
        assertEquals(58_000, budget(listOf(event("confirmed"), event(type="refund"), event(type="transfer"), event(type="card_payment"))).available)
        assertEquals(60_000, budget(listOf(event("confirmed"), event("confirmed", 2_000, "refund"))).available)
    }

    @Test fun financedPurchaseReservesOnlyCurrentChargeAndStopsAfterFinalMonth() {
        val purchase = event(amount=120_001).copy(installmentCount=3, firstInstallmentMonth="2026-11")
        assertEquals(40_001, budget(listOf(purchase)).pendingReserved)
        assertEquals(29_999, budget(listOf(purchase)).available)
        assertEquals(0, monthlyBudget(listOf(purchase), 100_000, 0, 0, 2026, Calendar.OCTOBER).pendingReserved)
        assertEquals(40_000, monthlyBudget(listOf(purchase), 100_000, 0, 0, 2027, Calendar.JANUARY).pendingReserved)
        assertEquals(0, monthlyBudget(listOf(purchase), 100_000, 0, 0, 2027, Calendar.FEBRUARY).pendingReserved)
    }

    @Test fun filtersSearchAmountsAccentsAndSelectedDateTogether() {
        assertTrue(movementMatches(event(), "cafe", "Pendientes", "Tarjeta", "2026-11-14"))
        assertTrue(movementMatches(event(), "$12.000", "Todos", "Todos", null))
        assertFalse(movementMatches(event(), "", "Confirmados", "Todos", null))
        assertFalse(movementMatches(event(), "", "Todos", "Cuenta", null))
        assertFalse(movementMatches(event(), "", "Todos", "Todos", "2026-11-15"))
        assertFalse(movementMatches(event("ignored"), "", "Todos", "Todos", null))
    }

    @Test fun validatesWholePesosAndCalendarDatesWithoutSilentCoercion() {
        assertEquals(123_456, parseClpInput("$123.456"))
        listOf("-1500", "1,5", "1.23", "9999999999999", "NaN").forEach { assertNull(parseClpInput(it)) }
        assertNotNull(parseDateInput("2028-02-29"))
        listOf("2026-02-29", "2026-11-14extra", "2026-2-2").forEach { assertNull(parseDateInput(it)) }
    }
}
