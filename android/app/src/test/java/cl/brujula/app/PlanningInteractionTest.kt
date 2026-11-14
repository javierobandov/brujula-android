package cl.brujula.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35])
class PlanningInteractionTest {
    private lateinit var context: Context
    @Before fun setup() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("brujula_planning", Context.MODE_PRIVATE).edit().clear().commit()
        context.deleteDatabase("finance_capture.db")
    }

    @Test fun editingPlanningPreservesIdentityAndCloudExportedSchedule() {
        val store = PlanningStore(context)
        store.addIncome("Sueldo", 100_000)
        val income = store.incomes().single()
        store.addIncome("Sueldo actualizado", 120_000, id=income.id)
        assertEquals(income.id, store.incomes().single().id)
        assertEquals(120_000, store.incomes().single().amount)
        store.addCommitment("Crédito", 10_000, 1, 6, "otros", firstInstallmentMonth="2026-11")
        val item = store.commitments().single()
        store.addCommitment("Crédito editado", 12_000, 1, 5, "otros", firstInstallmentMonth="2026-12", id=item.id)
        assertEquals(item.id, store.commitments().single().id)
        assertEquals(0, store.commitments().single().chargeAt(2026, Calendar.NOVEMBER))
        assertEquals(12_000, store.commitments().single().chargeAt(2027, Calendar.APRIL))
        assertEquals(0, store.commitments().single().chargeAt(2027, Calendar.MAY))
        val cloud = store.cloudData()
        context.getSharedPreferences("brujula_planning", Context.MODE_PRIVATE).edit().clear().commit()
        assertEquals(2, store.importCloudData(cloud))
        assertEquals("2026-12", store.commitments().single().firstInstallmentMonth)
        assertEquals(item.id, store.commitments().single().id)
    }

    @Test fun legacyLastInstallmentMigratesOnceAndIsIncludedThisMonth() {
        val legacy = JSONArray().put(JSONObject().put("id", "legacy").put("name", "Cuota").put("amount", 5_000).put("current_installment", 6).put("total_installments", 6))
        context.getSharedPreferences("brujula_planning", Context.MODE_PRIVATE).edit().putString("commitments", legacy.toString()).commit()
        val store = PlanningStore(context)
        val first = store.commitments().single()
        assertEquals(6, first.currentNumber)
        assertTrue(first.isActive)
        assertEquals(1, first.remainingInstallments)
        assertEquals(first, store.commitments().single())
        assertEquals(5_000, store.projections(2)[0].commitmentTotal)
        assertEquals(0, store.projections(2)[1].commitmentTotal)
        assertEquals("legacy", store.exportJson().getJSONArray("commitments").getJSONObject(0).getString("id"))
    }

    @Test fun manualInstallmentsSaveConfirmedAndIncompleteCaptureNeedsEditing() {
        val store = CapturedEventStore(context)
        store.insertManualExpense("Compra", 120_000, "compras", "tarjeta_credito", installmentCount=3, firstInstallmentMonth="2026-11")
        val manual = store.recent().single()
        assertEquals("confirmed", manual.status)
        assertEquals(3, manual.installmentCount)
        assertEquals("2026-11", manual.firstInstallmentMonth)
        store.insertSyncEvent("incomplete", System.currentTimeMillis()+1, "bank", "incomplete", null, null, "expense", "otros", "cuenta_corriente", "captured")
        val pending = store.recent().first { it.fingerprint == "incomplete" }
        assertFalse(store.confirmMovement(pending.id))
        store.updateMovement(pending, "Corregido", 10_000, pending.receivedAt, "salud", "cuenta_corriente")
        assertTrue(store.confirmMovement(pending.id))
        assertFalse(store.confirmMovement(pending.id))
        assertEquals("confirmed", store.recent().first { it.id == pending.id }.status)
        assertTrue(store.history(pending.id).isNotEmpty())
    }

    @Test fun bankMatchDeduplicatesEvidenceWithoutApprovingCapture() {
        val store = CapturedEventStore(context)
        val now = System.currentTimeMillis()
        store.insertSyncEvent("capture", now, "bank", "capture", 10_000, "Comercio", "expense", "otros", "tarjeta_credito", "captured")
        store.insertSyncEvent("statement", now, "cl.brujula.app.statement", "statement", 10_000, "Comercio", "expense", "otros", "tarjeta_credito", "requires_review")
        assertEquals(1, store.reconcileStatements())
        assertEquals("captured", store.recent().first { it.fingerprint=="capture" }.status)
        assertEquals(1, store.recent().count { it.isLedgerEvent })
        assertTrue(store.confirmMovement(store.recent().first { it.fingerprint=="capture" }.id))
        assertEquals(1, store.recent().count { it.isLedgerEvent })
    }
}
