package cl.brujula.app

import android.app.Notification
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import java.io.File
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
        val evidenceDir = File(InstrumentationRegistry.getInstrumentation().context.cacheDir, "test-evidence")
        evidenceDir.mkdirs()
        fun screenshot(name: String) {
            UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
                .takeScreenshot(File(evidenceDir, "$name.png"))
        }

        compose.onNodeWithText("BRÚJULA").assertExists()
        screenshot("01-resumen")

        val merchant = "Prueba UI ${UUID.randomUUID().toString().take(6)}"
        compose.onNodeWithText("＋  Agregar gasto").performClick()
        compose.onNodeWithText("Comercio o descripción").performTextInput(merchant)
        compose.onNodeWithText("Monto en pesos").performTextInput("8400")
        compose.onNodeWithText("Guardar").performClick()
        compose.onNodeWithText("Movimientos").performClick()
        compose.onNodeWithText(merchant).assertExists()
        screenshot("02-registro-manual")

        compose.onNodeWithText("Cuenta").performClick()
        compose.onNodeWithText("Herramientas avanzadas").performClick()
        screenshot("03-herramientas")
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
        store.close()

        compose.onNodeWithText("Actualizar").performClick()
        compose.onNodeWithContentDescription("Movimientos").performClick()
        compose.onNodeWithText("Comercio Sintético").performScrollTo()
        compose.onNodeWithText("Comercio Sintético").assertExists()
        compose.onNodeWithText("PAGO_TARJETA", substring = true).performScrollTo()
        compose.onNodeWithText("PAGO_TARJETA", substring = true).assertExists()
        screenshot("04-compra-y-pago-tarjeta")

        compose.onNodeWithContentDescription("Planificación").performClick()
        screenshot("05-planificacion")
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
        screenshot("06-cuotas")

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
        screenshot("07-exportacion-importacion")
    }
}
