package cl.brujula.app

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.text.NumberFormat
import java.util.Locale

@RunWith(AndroidJUnit4::class)
class FinancialInteractionTest {
    @get:Rule val ui = createEmptyComposeRule()
    private lateinit var context: Context
    private lateinit var scenario: ActivityScenario<MainActivity>

    @Before fun launch() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("finance_capture.db")
        context.getSharedPreferences("brujula_planning", Context.MODE_PRIVATE).edit().clear().commit()
        PlanningStore(context).addIncome("Sueldo", 100_000)
        CapturedEventStore(context).insertSyncEvent("pending-ui", System.currentTimeMillis(), "bank", "Compra Café", 10_000,
            "Café prueba", "expense", "alimentacion", "tarjeta_credito", "captured")
        scenario = ActivityScenario.launch(MainActivity::class.java)
        ui.waitForIdle()
    }
    @After fun close() { scenario.close() }
    private fun money(value: Int) = NumberFormat.getCurrencyInstance(Locale("es", "CL")).apply { maximumFractionDigits=0 }.format(value)
    private fun tab(label: String) { ui.onAllNodesWithText(label).onLast().performClick(); ui.waitForIdle() }
    private fun capture(name: String) {
        ui.waitForIdle()
        val file = File(context.getExternalFilesDir("ui-evidence"), "$name.png")
        file.parentFile?.mkdirs()
        file.outputStream().use { InstrumentationRegistry.getInstrumentation().uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test fun confirmKeepsAvailableAndPersistsAcrossRestart() {
        ui.onNodeWithTag("available-amount").assertTextEquals(money(90_000))
        capture("01-reserva-pendiente")
        tab("Movimientos")
        ui.onNodeWithTag("main-list").performScrollToNode(hasText("Confirmar movimiento"))
        ui.onNodeWithText("Confirmar movimiento").performScrollTo().performClick()
        assertEquals("confirmed", CapturedEventStore(context).recent().single().status)
        tab("Resumen")
        ui.onNodeWithTag("available-amount").assertTextEquals(money(90_000))
        scenario.recreate()
        ui.waitForIdle()
        ui.onNodeWithTag("available-amount").assertTextEquals(money(90_000))
        capture("02-confirmado-mismo-disponible")
    }

    @Test fun editPendingChangesAmountWithoutApprovingOrDuplicating() {
        val original = CapturedEventStore(context).recent().single()
        tab("Movimientos")
        ui.onNodeWithTag("main-list").performScrollToNode(hasText("Editar"))
        ui.onNodeWithText("Editar", useUnmergedTree=true).performScrollTo().performClick()
        ui.onNodeWithText("Monto total en pesos").performTextReplacement("15000")
        ui.onNodeWithText("Guardar pendiente").performClick()
        val edited = CapturedEventStore(context).recent().single()
        assertEquals(original.id, edited.id)
        assertEquals(original.receivedAt, edited.receivedAt)
        assertEquals("captured", edited.status)
        assertEquals(15_000, edited.amount)
        assertTrue(CapturedEventStore(context).history(edited.id).any { it.field=="amount" })
        tab("Resumen")
        ui.onNodeWithTag("available-amount").assertTextEquals(money(85_000))
        capture("03-edicion-pendiente")
    }

    @Test fun manualCardInstallmentsOnlyDeductMonthlyCharge() {
        ui.onNodeWithTag("main-list").performScrollToNode(hasText("＋  Agregar gasto"))
        ui.onNodeWithText("＋  Agregar gasto").performScrollTo().performClick()
        ui.onNodeWithText("Comercio o descripción").performTextInput("Compra cuotas")
        ui.onNodeWithText("Monto total en pesos").performTextInput("120000")
        ui.onNodeWithText("Tarjeta", useUnmergedTree=true).performScrollTo().performClick()
        ui.onNodeWithText("En cuotas").performScrollTo().performClick()
        ui.onNodeWithText("Número de cuotas").performScrollTo().performTextReplacement("3")
        ui.onNodeWithText("Guardar y confirmar").performClick()
        val purchase = CapturedEventStore(context).recent().single { it.merchant=="Compra cuotas" }
        assertEquals(120_000, purchase.amount)
        assertEquals(3, purchase.installmentCount)
        assertEquals("confirmed", purchase.status)
        ui.onNodeWithTag("available-amount").assertTextEquals(money(50_000))
        capture("04-compra-cuotas")
    }
}
