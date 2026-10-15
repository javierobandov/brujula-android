package cl.brujula.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class FirebaseCloudSync(context: Context) {
    private val appContext = context.applicationContext

    companion object {
        private val emulatorConfiguredApps = mutableSetOf<String>()
    }

    fun isConfigured(): Boolean =
        BuildConfig.FIREBASE_API_KEY.isNotBlank() &&
            BuildConfig.FIREBASE_APP_ID.isNotBlank() &&
            BuildConfig.FIREBASE_PROJECT_ID.isNotBlank()

    fun currentUserEmail(): String? = configuredAuth()?.currentUser?.email

    fun signIn(email: String, password: String, onResult: (String) -> Unit) {
        val auth = configuredAuth()
        if (auth == null) {
            onResult("Firebase no está configurado; la app sigue funcionando en modo local.")
            return
        }
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { onResult("Sesión iniciada para ${it.user?.email ?: email.trim()}.") }
            .addOnFailureListener { onResult("No se pudo iniciar sesión. Revisa el correo, la contraseña y que Email/Password esté habilitado en Firebase.") }
    }

    fun createAccount(email: String, password: String, onResult: (String) -> Unit) {
        val auth = configuredAuth()
        if (auth == null) {
            onResult("Firebase no está configurado; la app sigue funcionando en modo local.")
            return
        }
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { onResult("Cuenta creada para ${it.user?.email ?: email.trim()}.") }
            .addOnFailureListener { onResult("No se pudo crear la cuenta. Usa un correo válido y una contraseña de al menos 6 caracteres.") }
    }

    fun signOut() {
        configuredAuth()?.signOut()
    }

    fun sync(events: List<CapturedEvent>, planning: PlanningStore = PlanningStore(appContext), onResult: (String) -> Unit) {
        val app = firebaseApp()
        if (app == null) {
            onResult("Firebase no configurado; los datos siguen sólo en el teléfono.")
            return
        }

        val user = FirebaseAuth.getInstance(app).currentUser
        if (user == null) {
            onResult("Inicia sesión con una cuenta cloud para sincronizar entre teléfonos.")
            return
        }

        val uid = user.uid
        val firestore = FirebaseFirestore.getInstance(app)
        val collection = firestore.collection("users").document(uid).collection("events")
        val planningDocument = firestore.collection("users").document(uid).collection("planning").document("snapshot")
        val syncableEvents = events.filter { it.status != "demo" && it.status != "ignored" }
        val chunks = syncableEvents.chunked(450)

        fun pullRemote() {
            collection.get()
                .addOnSuccessListener { snapshot ->
                    val store = CapturedEventStore(appContext)
                    val recovered = snapshot.documents.count { document ->
                        val fingerprint = document.getString("fingerprint").orEmpty()
                        if (fingerprint.isBlank()) return@count false
                        store.insertSyncEvent(
                            fingerprint = fingerprint,
                            receivedAt = document.getLong("received_at") ?: System.currentTimeMillis(),
                            sourcePackage = document.getString("source_package") ?: "firebase",
                            rawText = document.getString("raw_text") ?: "Movimiento recuperado desde Brújula",
                            amount = document.getLong("amount")?.toInt(),
                            merchant = document.getString("merchant"),
                            movementType = document.getString("movement_type") ?: "expense",
                            category = document.getString("category") ?: "otros",
                            paymentSource = document.getString("payment_source") ?: "notificacion",
                            status = document.getString("status") ?: "captured"
                        )
                    }
                    store.close()
                    planningDocument.get()
                        .addOnSuccessListener { planningSnapshot ->
                            val planningRecovered = if (planningSnapshot.exists()) planning.importCloudData(planningSnapshot.data.orEmpty()) else 0
                            onResult("Sincronizados ${syncableEvents.size} y recuperados ${recovered} movimientos; planificación recuperada: $planningRecovered elementos.")
                        }
                        .addOnFailureListener { onResult("Movimientos sincronizados; no se pudo recuperar la planificación cloud.") }
                }
                .addOnFailureListener {
                    onResult("Datos locales sincronizados, pero no se pudieron recuperar cambios cloud.")
                }
        }

        fun commitChunk(index: Int) {
            if (index >= chunks.size) {
                planningDocument.get()
                    .addOnSuccessListener { snapshot ->
                        if (snapshot.exists()) planning.importCloudData(snapshot.data.orEmpty())
                        planningDocument.set(planning.cloudData())
                            .addOnSuccessListener { pullRemote() }
                            .addOnFailureListener { onResult("Movimientos sincronizados; no se pudo guardar la planificación cloud.") }
                    }
                    .addOnFailureListener { onResult("Movimientos sincronizados; no se pudo leer la planificación cloud.") }
                return
            }
            val batch = firestore.batch()
            chunks[index].forEach { event ->
                val data = hashMapOf<String, Any?>(
                    "schema_version" to 1,
                    "fingerprint" to event.fingerprint,
                    "received_at" to event.receivedAt,
                    "source_package" to event.sourcePackage,
                    // The original bank notification can contain sensitive details.
                    // Keep it local; the cloud only needs normalized accounting fields.
                    "raw_text" to "Movimiento capturado localmente",
                    "amount" to event.amount,
                    "merchant" to event.merchant,
                    "movement_type" to event.movementType,
                    "category" to event.category,
                    "payment_source" to event.paymentSource,
                    "status" to event.status,
                    "updated_at" to FieldValue.serverTimestamp()
                )
                batch.set(collection.document(event.fingerprint), data)
            }
            batch.commit()
                .addOnSuccessListener { commitChunk(index + 1) }
                .addOnFailureListener { onResult("No se pudo completar la sincronización; los datos locales no se modificaron.") }
        }

        commitChunk(0)
    }

    private fun configuredAuth(): FirebaseAuth? {
        val app = firebaseApp() ?: return null
        return FirebaseAuth.getInstance(app)
    }

    private fun firebaseApp(): FirebaseApp? {
        if (!isConfigured()) return null
        return runCatching {
            val app = FirebaseApp.getApps(appContext).firstOrNull()
                ?: FirebaseApp.initializeApp(
                    appContext,
                    FirebaseOptions.Builder()
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .build()
                )
            val emulatorHost = BuildConfig.FIREBASE_EMULATOR_HOST
            if (app != null && emulatorHost.isNotBlank()) {
                synchronized(emulatorConfiguredApps) {
                    if (app.name !in emulatorConfiguredApps) {
                        FirebaseAuth.getInstance(app).useEmulator(emulatorHost, 9099)
                        FirebaseFirestore.getInstance(app).useEmulator(emulatorHost, 8080)
                        emulatorConfiguredApps.add(app.name)
                    }
                }
            }
            app
        }.getOrNull()
    }
}
