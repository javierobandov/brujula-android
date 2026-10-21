package cl.brujula.app

import android.app.Notification
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.util.UUID
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BrujulaAppInstrumentedTest {
    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    @Test
    fun mainFlowsAndCardPaymentClassification() {
        compose.onNodeWithText("BRÚJULA").assertExists()

        val merchant = "Prueba UI ${UUID.randomUUID().toString().take(6)}"
        compose.onNodeWithTag("main-screen-list").performScrollToNode(hasText("Agregar gasto", substring = true))
        compose.onNodeWithText("Agregar gasto", substring = true).performClick()
        compose.onNodeWithText("Comercio o descripción").performTextInput(merchant)
        compose.onNodeWithText("Monto en pesos").performTextInput("8400")
        compose.onNodeWithText("Guardar").performClick()
        compose.onNodeWithTag("nav-tab-1").performClick()
        compose.onNodeWithText(merchant).assertExists()

        compose.onNodeWithTag("nav-tab-4").performClick()
        compose.onNodeWithTag("main-screen-list").performScrollToNode(hasText("Herramientas avanzadas"))
        compose.onNodeWithText("Herramientas avanzadas").performClick()
        compose.onNodeWithText("Probar clasificación").performClick()
        val store = CapturedEventStore(compose.activity)
        val demoEvents = store.recent(10_000).filter { it.status == "demo" }
        assertEquals("La prueba local debe insertar las cuatro muestras", 4, demoEvents.size)
        compose.onNodeWithText("Bandeja capturada").assertExists()

        val testTime = System.currentTimeMillis()
        val purchaseNotification = Notification.Builder(compose.activity, "brujula-test")
            .setContentTitle("Scotiabank")
            .setContentText("Compra con tarjeta en Comercio Sintético por $8.400")
            .build()
        val paymentNotification = Notification.Builder(compose.activity, "brujula-test")
            .setContentTitle("Scotiabank")
            .setContentText("Pago de tu tarjeta de crédito por $8.400")
            .build()
        val purchaseText = notificationText(purchaseNotification)
        val paymentText = notificationText(paymentNotification)
        assertTrue(looksFinancial(purchaseText, "com.scotiabank.test"))
        assertTrue(looksFinancial(paymentText, "com.scotiabank.test"))
        assertTrue(!looksFinancial("Inicio de sesión exitoso", "com.scotiabank.test"))
        assertTrue(store.insertIfNew("com.scotiabank.test", purchaseText, testTime))
        assertTrue(store.insertIfNew("com.scotiabank.test", paymentText, testTime + 1))
        assertTrue(!store.insertIfNew("com.scotiabank.test", purchaseText, testTime))
        val cardPair = store.recent(10_000).filter { it.receivedAt >= testTime && it.receivedAt <= testTime + 1 }
        assertEquals(2, cardPair.size)
        assertEquals(1, cardPair.count { it.movementType == "expense" })
        assertEquals(1, cardPair.count { it.movementType == "card_payment" })
        assertEquals(8_400, expenseMovements(cardPair).sumOf { it.amount ?: 0 })
        val classifiedPurchase = cardPair.single { it.movementType == "expense" }
        assertEquals("Comercio Sintético", classifiedPurchase.merchant)
        assertEquals(8_400, classifiedPurchase.amount)
        assertEquals("tarjeta_credito", classifiedPurchase.paymentSource)
        store.close()

        compose.onNodeWithContentDescription("Planificación").performClick()
        compose.onNodeWithText("Ingreso").performClick()
        compose.onNodeWithText("Nombre: sueldo, DAP, Integra…").performTextInput("Ingreso de prueba")
        compose.onNodeWithText("Monto mensual").performTextInput("900000")
        compose.onNodeWithText("Guardar").performClick()
        compose.onNodeWithText("Ingreso de prueba", substring = true).assertExists()

        compose.onNodeWithText("Compromiso").performClick()
        compose.onNodeWithText("Ej.: PC, diplomado, mamá").performTextInput("Cuota de prueba")
        compose.onNodeWithText("Monto de la cuota").performTextInput("12000")
        compose.onNodeWithText("Cuota actual").performTextInput("1")
        compose.onNodeWithText("Total cuotas").performTextInput("3")
        compose.onNodeWithText("Guardar").performClick()
        compose.onNodeWithText("Cuota de prueba", substring = true).assertExists()

        val csvSeedStore = CapturedEventStore(compose.activity)
        csvSeedStore.insertManualExpense("CSV Servicio", 5_100, "servicios", "manual", testTime + 2)
        csvSeedStore.close()
        val allEventsStore = CapturedEventStore(compose.activity)
        val allEvents = allEventsStore.recent(10_000)
        allEventsStore.close()
        val csv = capturedCsv(allEvents)
        val importedCsv = importCapturedCsv(compose.activity, csv)
        assertTrue("CSV exportado debe poder importarse", importedCsv >= 1)
        assertEquals("La segunda importación CSV debe ser idempotente", 0, importCapturedCsv(compose.activity, csv))
        val roundTripStore = CapturedEventStore(compose.activity)
        val csvRoundTrip = roundTripStore.recent(10_000).single {
            it.sourcePackage == "cl.brujula.app.import" && it.merchant == "CSV Servicio"
        }
        assertEquals("servicios", csvRoundTrip.category)
        roundTripStore.close()

        val backupEvent = CapturedEvent(
            id = -1,
            fingerprint = "backup-fixture-${UUID.randomUUID()}",
            receivedAt = System.currentTimeMillis(),
            sourcePackage = "cl.brujula.app.test",
            rawText = "Movimiento sintético de respaldo",
            amount = 3_200,
            merchant = "Importación de respaldo",
            movementType = "expense",
            category = "compras",
            paymentSource = "manual",
            status = "captured"
        )
        val backup = syncBundleJson(listOf(backupEvent), PlanningStore(compose.activity).exportJson())
        assertEquals(1, importSyncBundle(compose.activity, backup))
        assertEquals("La segunda importación JSON debe ser idempotente", 0, importSyncBundle(compose.activity, backup))
    }
}
