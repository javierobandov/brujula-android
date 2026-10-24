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
                03/10/2026 DEVOLUCION COMERCIO $1.500 $1.500 01/01 $1.500
                04/10/2026 PAGO TARJETA CREDITO $40.000 $40.000 01/01 $40.000
                05/10/2026 AVANCE EFECTIVO EN CUOTAS $15.000 $2.500 01/06 $2.500
                06/10/2026
                PAGO EN EFECTIVO
                $35.000 $35.000 00/00 $35.000
                PAGOS A LA CUENTA $35.000
                07/10/2026 COMPRA EJEMPLO $6.000 $6.000 01/01 $6.000
            """.trimIndent(),
            2026
        )

        assertEquals(listOf("refund", "card_payment", "transfer", "card_payment", "expense"), rows.map { it.movementType })
        assertEquals(listOf(1_500, 40_000, 2_500, 35_000, 6_000), rows.map { it.amount })
        assertEquals("cuenta_corriente", rows[1].paymentSource)
        assertEquals("tarjeta_credito", rows[0].paymentSource)
    }

    @Test
    fun identicalSameDayPurchasesAreNotCollapsedAndRowsWithoutDateAreIgnored() {
        val text = """
            COMERCIO EJEMPLO $4.000 $4.000 01/01 $4.000
            06/10/2026 COMERCIO EJEMPLO $4.000 $4.000 01/01 $4.000
            06/10/2026 COMERCIO EJEMPLO $4.000 $4.000 01/01 $4.000
        """.trimIndent()

        val rows = ScotiaStatementPdfParser.parse(text, 2026)
        assertEquals(2, rows.size)
        assertEquals(listOf(1, 2), rows.map { it.occurrence })
        assertTrue(rows.first().receivedAt > 0)
    }
    @Test
    fun ignoresPaymentSlipsTotalsAndDetachedAmountColumns() {
        val rows = ScotiaStatementPdfParser.parse(
            """
                II. DETALLE
                1. PERÍODO ANTERIOR
                09/11/2026
                Monto Mínimo a Pagar
                $12.000
                Monto Total Facturado a Pagar $80.000
                2. DETALLE DE OPERACIONES
                12/10/2026
                COMERCIO EJEMPLO $8.000 $8.000 01/01 $8.000
                COMERCIO EJEMPLO $8.000
                CARGO MANTENCION $1.000 $1.000 01/01 $1.000
                TOTAL OPERACIONES $9.000
                III. INFORMACIÓN DE PAGO
            """.trimIndent(),
            2026
        )

        assertEquals(2, rows.size)
        assertEquals(listOf(8_000, 1_000), rows.map { it.amount })
        assertEquals(listOf("expense", "expense"), rows.map { it.movementType })
    }
}
