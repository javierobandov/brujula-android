package cl.brujula.app

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScotiaPurchaseNotificationTest {
    private val bankPackage = "cl.scotiabank.app"

    @Test fun realCreditCardPurchaseIsCaptured() {
        val text = "App Scotia · Se realizó compra con tu tarjeta de crédito xxxx2236 por $2.125 en MERPAGO*ANTARTI. Si desconoces esta operación puedes apagar o bloquear tus tarjetas."
        assertTrue(isScotiaPurchaseNotification(text, bankPackage))
    }

    @Test fun legitimateDebitPurchaseIsCaptured() {
        val text = "App Scotia · Se realizó compra con tu tarjeta de débito xxxx2236 por $12.125 en COMERCIO PRUEBA ·"
        assertTrue(isScotiaPurchaseNotification(text, bankPackage))
    }

    @Test fun bankNewsWithNumbersIsNotAnExpense() {
        assertFalse(isScotiaPurchaseNotification("App Scotia · Infórmate: 48 cuotas de $978", bankPackage))
    }

    @Test fun cardPaymentIsNotAnExpense() {
        assertFalse(isScotiaPurchaseNotification("App Scotia · Se realizó pago de tu tarjeta de crédito por $100.000", bankPackage))
    }

    @Test fun transferIsNotAnExpense() {
        assertFalse(isScotiaPurchaseNotification("App Scotia · Se realizó transferencia por $45.000 en cuenta destino", bankPackage))
    }

    @Test fun unrelatedAppCannotForgePurchase() {
        assertFalse(isScotiaPurchaseNotification("Se realizó compra con tu tarjeta de crédito por $2.125 en TEST", "com.other.app"))
    }

    @Test fun incompletePurchaseIsRejected() {
        assertFalse(isScotiaPurchaseNotification("App Scotia · Se realizó compra con tu tarjeta de crédito por $2.125", bankPackage))
    }
}
