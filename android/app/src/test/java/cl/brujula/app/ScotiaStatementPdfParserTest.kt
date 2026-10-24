package cl.brujula.app

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class ScotiaStatementPdfParserTest {
    @Test
    fun importsPurchasesAndUsesCurrentInstallmentAmountWithoutImportingTotals() {
        val movements = ScotiaStatementPdfParser.parse(
            """
                II. DETALLE
                14/10/2026
                COMERCIO EJEMPLO
                $120.000 $10.000 02/12 $10.000
                1. TOTAL OPERACIONES $10.000
                III. INFORMACIÓN DE PAGO
            """.trimIndent(),
            2026
        )

        assertEquals(1, movements.size)
        assertEquals("COMERCIO EJEMPLO", movements.single().merchant)
        assertEquals(10_000, movements.single().amount)
        assertEquals("02/12", movements.single().installment)
        assertEquals("expense", movements.single().movementType)
        val date = Calendar.getInstance().apply { timeInMillis = movements.single().receivedAt }
        assertEquals(2026, date.get(Calendar.YEAR))
        assertEquals(Calendar.OCTOBER, date.get(Calendar.MONTH))
        assertEquals(14, date.get(Calendar.DAY_OF_MONTH))
    }

    @Test
    fun classifiesRefundCardPaymentAndCashAdvanceSeparatelyFromExpenses() {
        val rows = ScotiaStatementPdfParser.parse(
            """
                03/10/2026 DEVOLUCION COMERCIO $1.500
                04/10/2026 PAGO TARJETA CREDITO $40.000
                05/10/2026 AVANCE EFECTIVO EN CUOTAS $15.000 $2.500 01/06 $2.500
            """.trimIndent(),
            2026
        )

        assertEquals(listOf("refund", "card_payment", "transfer"), rows.map { it.movementType })
        assertEquals(listOf(1_500, 40_000, 2_500), rows.map { it.amount })
        assertEquals("cuenta_corriente", rows[1].paymentSource)
        assertEquals("tarjeta_credito", rows[0].paymentSource)
    }

    @Test
    fun identicalSameDayPurchasesAreNotCollapsedAndRowsWithoutDateAreIgnored() {
        val text = """
            COMERCIO EJEMPLO $4.000
            06/10/2026 COMERCIO EJEMPLO $4.000
            06/10/2026 COMERCIO EJEMPLO $4.000
        """.trimIndent()

        val rows = ScotiaStatementPdfParser.parse(text, 2026)
        assertEquals(2, rows.size)
        assertEquals(listOf(1, 2), rows.map { it.occurrence })
        assertTrue(rows.first().receivedAt > 0)
    }
}
