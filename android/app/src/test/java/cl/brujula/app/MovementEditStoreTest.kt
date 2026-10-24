package cl.brujula.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class MovementEditStoreTest {
    private lateinit var context: Context

    @Before fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("finance_capture.db")
    }

    @After fun tearDown() { context.deleteDatabase("finance_capture.db") }

    @Test fun editPreservesNotificationAndRecordsEveryChangedValue() {
        val store = CapturedEventStore(context)
        val fingerprint = "edit-test"
        store.insertSyncEvent(fingerprint, 1_800_000_000_000L, "cl.brujula.app.scotia", "original notification", 12_345,
            "Original", "expense", "otros", "cuenta_corriente", "confirmed")
        val original = store.recent(10).single()

        assertEquals(1, store.updateMovement(original, "Comercio corregido", 15_000, original.receivedAt + 86_400_000,
            "alimentacion", "tarjeta_credito"))

        val edited = store.recent(10).single()
        assertEquals("original notification", edited.rawText)
        assertEquals("Comercio corregido", edited.merchant)
        assertEquals(15_000, edited.amount)
        assertEquals("confirmed", edited.status)
        assertTrue(edited.manuallyEdited)
        assertEquals(setOf("merchant", "amount", "received_at", "category", "payment_source"), store.history(edited.id).map { it.field }.toSet())
    }

    @Test fun staleSyncCannotReplaceManualEditOrReconciliationAndIgnoredStatusPersists() {
        val store = CapturedEventStore(context)
        val fingerprint = "stale-sync-test"
        store.insertSyncEvent(fingerprint, 1_800_000_000_000L, "cl.brujula.app.scotia", "original", 10_000,
            "Original", "expense", "otros", "cuenta_corriente", "confirmed")
        val original = store.recent(10).single()
        store.updateMovement(original, "Manual", 20_000, original.receivedAt, "salud", "tarjeta_credito")
        store.insertSyncEvent(fingerprint, original.receivedAt, "cl.brujula.app.scotia", "stale cloud copy", 10_000,
            "Original", "expense", "otros", "cuenta_corriente", "reconciled")

        val merged = store.recent(10).single()
        assertEquals("Manual", merged.merchant)
        assertEquals(20_000, merged.amount)
        assertEquals("salud", merged.category)
        assertEquals("reconciled", merged.status)

        store.updateStatus(merged.id, "ignored")
        store.insertSyncEvent(fingerprint, original.receivedAt, "cl.brujula.app.scotia", "stale cloud copy", 10_000,
            "Original", "expense", "otros", "cuenta_corriente", "confirmed")
        assertFalse(store.recent(10).any { it.fingerprint == fingerprint })
        assertEquals("ignored", store.recent(10, includeIgnored = true).single().status)
    }

    @Test fun importedManualEditAndHistoryApplyToExistingUneditedMovement() {
        val store = CapturedEventStore(context)
        val fingerprint = "manual-import-test"
        val timestamp = 1_800_000_000_000L
        store.insertSyncEvent(fingerprint, timestamp, "cl.brujula.app.scotia", "original", 10_000,
            "Original", "expense", "otros", "cuenta_corriente", "confirmed")
        store.insertSyncEvent(fingerprint, timestamp + 1, "cl.brujula.app.scotia", "original", 25_000,
            "Corrected", "expense", "salud", "tarjeta_credito", "confirmed", categoryManual = true, manuallyEdited = true)
        store.importHistory(fingerprint, listOf(MovementChange(0, 0, timestamp + 2, "merchant", "Original", "Corrected")))

        val merged = store.recent(10).single()
        assertEquals("Corrected", merged.merchant)
        assertEquals(25_000, merged.amount)
        assertEquals("salud", merged.category)
        assertEquals("tarjeta_credito", merged.paymentSource)
        assertTrue(merged.manuallyEdited)
        assertEquals("Original", store.history(merged.id).single().oldValue)
    }
}
