package cl.brujula.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class FirebaseEmulatorInstrumentedTest {
    @Test
    fun syncAndRecoverEventsAndPlanningFromFirebaseEmulator() {
        assumeTrue("Firebase Emulator must be configured for this test", BuildConfig.FIREBASE_EMULATOR_HOST.isNotBlank())
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // The visual-flow test runs in the same emulator and may leave synthetic local data behind.
        context.deleteDatabase("finance_capture.db")
        context.getSharedPreferences("brujula_planning", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val cloud = FirebaseCloudSync(context)
        val email = "test-${UUID.randomUUID()}@brujula.invalid"
        val password = UUID.randomUUID().toString().take(20) + "A1!"
        val account = awaitResult { callback -> cloud.createAccount(email, password, callback) }
        assertTrue("Firebase Auth emulator account creation failed: $account", cloud.currentUserEmail() == email)

        val originalEvents = CapturedEventStore(context)
        val receivedAt = System.currentTimeMillis()
        assertTrue(originalEvents.insertIfNew(
            "com.scotiabank.test",
            "Scotiabank: compra sintética en Comercio de Prueba por $18.250",
            receivedAt
        ))
        val originalPlanning = PlanningStore(context)
        originalPlanning.addIncome("Ingreso cloud de prueba", 750_000)
        originalPlanning.addCommitment("Cuota cloud", 25_000, 2, 6, "servicios")
        originalPlanning.setMonthlyGoal(80_000)
        originalPlanning.setCategoryLimit("alimentacion", 150_000)
        val initialSync = awaitResult { callback -> cloud.sync(originalEvents.recent(10_000), originalPlanning, callback) }
        assertTrue("Initial sync failed: $initialSync", initialSync.startsWith("Sincronizados"))
        originalEvents.close()

        context.deleteDatabase("finance_capture.db")
        context.getSharedPreferences("brujula_planning", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        val recoveredPlanning = PlanningStore(context)
        assertTrue(recoveredPlanning.incomes().isEmpty())
        val recoveryMessage = awaitResult { callback -> cloud.sync(emptyList(), recoveredPlanning, callback) }

        val recoveredEvents = CapturedEventStore(context)
        val recoveredEvent = recoveredEvents.recent(10_000).singleOrNull {
            it.merchant?.contains("Comercio de Prueba", ignoreCase = true) == true
        }
        assertNotNull("Movement was not recovered: $recoveryMessage", recoveredEvent)
        assertEquals("Ingreso cloud de prueba", recoveredPlanning.incomes().single().name)
        assertEquals(80_000, recoveredPlanning.monthlyGoal())
        assertEquals(150_000, recoveredPlanning.categoryLimit("alimentacion"))
        assertEquals("Cuota cloud", recoveredPlanning.commitments().single().name)
        assertEquals(2, recoveredPlanning.commitments().single().currentInstallment)
        assertEquals(6, recoveredPlanning.commitments().single().totalInstallments)
        val repeatedSync = awaitResult { callback -> cloud.sync(recoveredEvents.recent(10_000), recoveredPlanning, callback) }
        assertTrue("Repeated sync failed: $repeatedSync", repeatedSync.startsWith("Sincronizados"))
        assertEquals(1, recoveredEvents.recent(10_000).count { it.fingerprint == recoveredEvent?.fingerprint })
        recoveredEvents.close()
        cloud.signOut()
    }

    private fun awaitResult(action: ((String) -> Unit) -> Unit): String {
        val latch = CountDownLatch(1)
        val result = AtomicReference<String>()
        action { message -> result.set(message); latch.countDown() }
        assertTrue("Timed out waiting for Firebase Emulator", latch.await(60, TimeUnit.SECONDS))
        return result.get()
    }
}
