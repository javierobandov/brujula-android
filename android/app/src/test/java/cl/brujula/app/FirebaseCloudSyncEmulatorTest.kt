package cl.brujula.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows
import android.os.Looper
import java.util.concurrent.Executor
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class FirebaseCloudSyncEmulatorTest {
    private lateinit var context: Context
    private lateinit var app: FirebaseApp
    private lateinit var auth: FirebaseAuth
    private lateinit var firestore: FirebaseFirestore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase("finance_capture.db")
        context.getSharedPreferences("brujula_planning", Context.MODE_PRIVATE).edit().clear().commit()
        app = FirebaseApp.initializeApp(
            context,
            FirebaseOptions.Builder()
                .setApplicationId("1:1234567890:android:synthetic")
                .setApiKey("fake-api-key")
                .setProjectId("demo-brujula")
                .build(),
            "brujula-emulator-test-${UUID.randomUUID()}"
        )
        auth = FirebaseAuth.getInstance(app).apply { useEmulator("127.0.0.1", 9099) }
        firestore = FirebaseFirestore.getInstance(app).apply { useEmulator("127.0.0.1", 8080) }
        awaitTask(auth.createUserWithEmailAndPassword("test-${UUID.randomUUID()}@example.test", "synthetic-password-123"))
    }

    @After
    fun tearDown() {
        app.delete()
        context.deleteDatabase("finance_capture.db")
        context.getSharedPreferences("brujula_planning", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test
    fun pullsCloudEventPreservesManualLocalStateAndCanSyncRepeatedly() {
        val userId = requireNotNull(auth.currentUser?.uid)
        val fingerprint = "synthetic-${UUID.randomUUID()}"
        val remoteEvent = mapOf(
            "schema_version" to 1,
            "fingerprint" to fingerprint,
            "received_at" to 1_800_000_000_000L,
            "source_package" to "cl.brujula.app.statement",
            "raw_text" to "Movimiento sintético",
            "amount" to 12_345,
            "merchant" to "Comercio Sintético",
            "movement_type" to "expense",
            "category" to "otros",
            "category_manual" to false,
            "payment_source" to "cartola",
            "status" to "requires_review"
        )
        awaitTask(
            firestore.collection("users").document(userId).collection("events").document(fingerprint).set(remoteEvent),
        )

        val store = CapturedEventStore(context)
        store.insertSyncEvent(
            fingerprint = fingerprint,
            receivedAt = 1_800_000_000_100L,
            sourcePackage = "cl.brujula.app.manual",
            rawText = "Registro local sintético",
            amount = 12_345,
            merchant = "Comercio Sintético",
            movementType = "expense",
            category = "transporte",
            paymentSource = "debito",
            status = "reconciled",
            categoryManual = true
        )

        syncAndAwait()
        val local = store.recent(100).single { it.fingerprint == fingerprint }
        assertEquals("reconciled", local.status)
        assertEquals("transporte", local.category)
        assertTrue(local.categoryManual)

        syncAndAwait()
        val cloud = requireNotNull(awaitTask(
            firestore.collection("users").document(userId).collection("events").document(fingerprint).get(Source.SERVER),
        ))
        assertTrue(cloud.exists())
        assertEquals("reconciled", cloud.getString("status"))
        assertEquals("transporte", cloud.getString("category"))
        assertEquals(true, cloud.getBoolean("category_manual"))
        val planning = requireNotNull(awaitTask(
            firestore.collection("users").document(userId).collection("planning").document("snapshot").get(Source.SERVER),
        ))
        assertNotNull(planning.data)
        assertEquals(1L, planning.getLong("schema_version"))
    }

    @Test
    fun failedServerReadDoesNotUploadAndRetryRecoversLocalEvent() {
        val userId = requireNotNull(auth.currentUser?.uid)
        val fingerprint = "retry-${UUID.randomUUID()}"
        val store = CapturedEventStore(context)
        store.insertSyncEvent(
            fingerprint = fingerprint,
            receivedAt = 1_800_000_000_200L,
            sourcePackage = "cl.brujula.app.manual",
            rawText = "Registro local sintético",
            amount = 8_000,
            merchant = "Comercio Local",
            movementType = "expense",
            category = "otros",
            paymentSource = "debito",
            status = "captured"
        )

        awaitTask(firestore.disableNetwork())
        val failedResult = syncAndAwaitResult()
        assertTrue("Expected a server-read failure, got: $failedResult", failedResult?.contains("no se subieron cambios") == true)
        awaitTask(firestore.enableNetwork())

        syncAndAwait()
        val cloudEvent = requireNotNull(awaitTask(
            firestore.collection("users").document(userId).collection("events").document(fingerprint).get(Source.SERVER),
        ))
        assertTrue(cloudEvent.exists())
        assertEquals(8_000L, cloudEvent.getLong("amount"))
    }

    @Test
    fun recoversAndUploadsAcrossFirestoreBatchBoundary() {
        val userId = requireNotNull(auth.currentUser?.uid)
        val collection = firestore.collection("users").document(userId).collection("events")
        val total = 451
        (0 until total).chunked(400).forEach { indexes ->
            val batch = firestore.batch()
            indexes.forEach { index ->
                val fingerprint = "bulk-$index-${UUID.randomUUID()}"
                batch.set(collection.document(fingerprint), mapOf(
                    "schema_version" to 1,
                    "fingerprint" to fingerprint,
                    "received_at" to 1_800_000_000_000L + index,
                    "source_package" to "cl.brujula.app.statement",
                    "raw_text" to "synthetic",
                    "amount" to index + 1,
                    "merchant" to "Synthetic merchant $index",
                    "movement_type" to "expense",
                    "category" to "otros",
                    "payment_source" to "cartola",
                    "status" to "requires_review"
                ))
            }
            awaitTask(batch.commit())
        }

        syncAndAwait()

        val localEvents = CapturedEventStore(context).recent(Int.MAX_VALUE)
        assertEquals(total, localEvents.size)
        val cloudEvents = requireNotNull(awaitTask(collection.get(Source.SERVER)))
        assertEquals(total, cloudEvents.size())
    }

    private fun syncAndAwait() {
        val result = syncAndAwaitResult()
        assertTrue("Firebase sync reported an error: $result", result?.startsWith("Sincronizados ") == true)
    }

    private fun syncAndAwaitResult(): String? {
        val latch = CountDownLatch(1)
        var result: String? = null
        FirebaseCloudSync(context).sync(emptyList(), PlanningStore(context)) {
            result = it
            latch.countDown()
        }
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (latch.count > 0 && System.nanoTime() < deadline) {
            mainLooper.idleFor(10, TimeUnit.MILLISECONDS)
            latch.await(10, TimeUnit.MILLISECONDS)
        }
        assertEquals("Firebase sync callback timed out", 0L, latch.count)
        return result
    }

    private fun <T> awaitTask(task: Task<T>): T? {
        val latch = CountDownLatch(1)
        var value: T? = null
        var error: Exception? = null
        task.addOnCompleteListener(Executor { it.run() }) { completed ->
            if (completed.isSuccessful) value = completed.result else error = completed.exception
            latch.countDown()
        }
        val mainLooper = Shadows.shadowOf(Looper.getMainLooper())
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(20)
        while (latch.count > 0 && System.nanoTime() < deadline) {
            mainLooper.idleFor(10, TimeUnit.MILLISECONDS)
            latch.await(10, TimeUnit.MILLISECONDS)
        }
        assertEquals("Firebase task timed out", 0L, latch.count)
        error?.let { throw AssertionError("Firebase task failed", it) }
        return value
    }
}
