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

    fun sync(events: List<CapturedEvent>, onResult: (String) -> Unit) {
        if (!isConfigured()) {
            onResult("Firebase no configurado; los datos siguen sólo en el teléfono.")
            return
        }

        val app = runCatching {
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

        if (app == null) {
            onResult("No se pudo inicializar Firebase; los datos locales no se modificaron.")
            return
        }

        FirebaseAuth.getInstance(app).signInAnonymously()
            .addOnFailureListener { onResult("Firebase no disponible; se mantienen los datos locales.") }
            .addOnSuccessListener { result ->
                val uid = result.user?.uid
                if (uid.isNullOrBlank()) {
                    onResult("Firebase no entregó una identidad; se mantienen los datos locales.")
                    return@addOnSuccessListener
                }
                val firestore = FirebaseFirestore.getInstance(app)
                val batch = firestore.batch()
                val collection = firestore.collection("users").document(uid).collection("events")
                events.forEach { event ->
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
                    .addOnSuccessListener { onResult("Sincronizados ${events.size} movimientos en Firebase.") }
                    .addOnFailureListener { onResult("No se pudo completar la sincronización; los datos locales no se modificaron.") }
            }
    }
}
