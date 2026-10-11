package cl.brujula.app

import android.content.Context
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore

class FirebaseCloudSync(context: Context) {
    private val appContext = context.applicationContext

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
            .addOnSuccessListener { onResult("Sesión iniciada para \${it.user?.email ?: email.trim()}.") }
            .addOnFailureListener { onResult("No se pudo iniciar sesión. Revisa el correo, la contraseña y que Email/Password esté habilitado en Firebase.") }
    }

    fun createAccount(email: String, password: String, onResult: (String) -> Unit) {
        val auth = configuredAuth()
        if (auth == null) {
            onResult("Firebase no está configurado; la app sigue funcionando en modo local.")
            return
        }
        auth.createUserWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { onResult("Cuenta creada para \${it.user?.email ?: email.trim()}.") }
            .addOnFailureListener { onResult("No se pudo crear la cuenta. Usa un correo válido y una contraseña de al menos 6 caracteres.") }
    }

    fun signOut() {
        configuredAuth()?.signOut()
    }

    fun sync(events: List<CapturedEvent>, onResult: (String) -> Unit) {
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
        val chunks = events.chunked(450)

        fun commitChunk(index: Int) {
            if (index >= chunks.size) {
                onResult("Sincronizados \${events.size} movimientos en Firebase.")
                return
            }
            val batch = firestore.batch()
            chunks[index].forEach { event ->
                val data = hashMapOf<String, Any?>(
                    "schema_version" to 1,
                    "fingerprint" to event.fingerprint,
                    "received_at" to event.receivedAt,
                    "source_package" to event.sourcePackage,
                    "raw_text" to event.rawText,
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
            FirebaseApp.getApps(appContext).firstOrNull()
                ?: FirebaseApp.initializeApp(
                    appContext,
                    FirebaseOptions.Builder()
                        .setApiKey(BuildConfig.FIREBASE_API_KEY)
                        .setApplicationId(BuildConfig.FIREBASE_APP_ID)
                        .setProjectId(BuildConfig.FIREBASE_PROJECT_ID)
                        .build()
                )
        }.getOrNull()
    }
}
