package cl.brujula.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.util.Locale

data class CapturedEvent(
    val id: Long,
    val fingerprint: String,
    val receivedAt: Long,
    val sourcePackage: String,
    val rawText: String,
    val amount: Int?,
    val merchant: String?,
    val movementType: String,
    val category: String,
    val paymentSource: String,
    val status: String,
    val categoryManual: Boolean = false,
    val manuallyEdited: Boolean = false
)

data class MovementChange(val id: Long, val eventId: Long, val changedAt: Long, val field: String, val oldValue: String?, val newValue: String?)

internal data class ReconciliationRecord(
    val id: Long,
    val receivedAt: Long,
    val amount: Int,
    val merchant: String,
    val movementType: String = "expense"
)

/** Returns only mutual one-to-one matches, so an ambiguous merchant never gets reconciled automatically. */
internal fun determineReconciliationPairs(
    statements: List<ReconciliationRecord>,
    captured: List<ReconciliationRecord>
): List<Pair<Long, Long>> {
    fun compatible(statement: ReconciliationRecord, event: ReconciliationRecord): Boolean {
        if (statement.amount != event.amount || statement.movementType != event.movementType) return false
        if (kotlin.math.abs(statement.receivedAt - event.receivedAt) > 7L * 24 * 60 * 60 * 1000) return false
        val left = normalizeMerchant(statement.merchant)
        val right = normalizeMerchant(event.merchant)
        return left.isNotBlank() && right.isNotBlank() &&
            (left == right || (minOf(left.length, right.length) >= 5 && (left.contains(right) || right.contains(left))))
    }

    val orderedStatements = statements.sortedWith(compareBy<ReconciliationRecord> { it.receivedAt }.thenBy { it.id })
    val orderedCaptured = captured.sortedWith(compareBy<ReconciliationRecord> { it.receivedAt }.thenBy { it.id })
    val statementCandidates = orderedStatements.associate { statement ->
        statement.id to orderedCaptured.filter { compatible(statement, it) }.map { it.id }
    }
    val capturedCandidates = orderedCaptured.associate { event ->
        event.id to orderedStatements.filter { compatible(it, event) }.map { it.id }
    }
    return orderedStatements.mapNotNull { statement ->
        val candidates = statementCandidates[statement.id].orEmpty()
        val eventId = candidates.singleOrNull() ?: return@mapNotNull null
        if (capturedCandidates[eventId].orEmpty().size == 1) statement.id to eventId else null
    }
}

private fun normalizeMerchant(value: String): String = value.lowercase(Locale.ROOT)
    .replace(Regex("[^a-z0-9áéíóúñ]"), "")

class CapturedEventStore(context: Context) {
    private val helper = EventDb(context.applicationContext)

    fun insertIfNew(sourcePackage: String, rawText: String, receivedAt: Long, status: String = "captured"): Boolean {
        val db = helper.writableDatabase
        val fingerprint = sha256("$sourcePackage|$rawText|${receivedAt / 60_000}")
        val values = ContentValues().apply {
            put("fingerprint", fingerprint)
            put("received_at", receivedAt)
            put("source_package", sourcePackage)
            put("raw_text", rawText)
            put("amount", parseAmount(rawText))
            put("merchant", parseMerchant(rawText))
            put("movement_type", classifyMovement(rawText))
            put("category", classifyCategory(rawText))
            put("payment_source", classifyPaymentSource(rawText))
            put("status", status)
        }
        return db.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun insertDemoEvent(receivedAt: Long = System.currentTimeMillis()): Boolean =
        insertIfNew(
            sourcePackage = "cl.brujula.app.demo",
            rawText = "DEMO · Scotiabank: compra de prueba en Comercio Ejemplo por \\$12.345",
            receivedAt = receivedAt,
            status = "demo"
        )


    fun insertClassificationDemoEvents(receivedAt: Long = System.currentTimeMillis()): Int {
        val samples = listOf(
            "DEMO · Scotiabank: compra en Comercio Ejemplo por \\$12.345",
            "DEMO · Scotiabank: pago de tu tarjeta de crédito por \\$50.000",
            "DEMO · Scotiabank: transferencia por \\$15.000 a Cuenta Ejemplo",
            "DEMO · Scotiabank: devolución por \\$7.000 de Comercio Ejemplo"
        )
        return samples.mapIndexed { index, text ->
            insertIfNew("cl.brujula.app.demo", text, receivedAt + index * 60_000L, status = "demo")
        }.count { it }
    }

    fun insertManualExpense(
        merchant: String,
        amount: Int,
        category: String,
        paymentSource: String,
        receivedAt: Long = System.currentTimeMillis()
    ): Boolean {
        val db = helper.writableDatabase
        val rawText = "MANUAL · $merchant · $amount · $paymentSource"
        val values = ContentValues().apply {
            put("fingerprint", sha256("manual|$merchant|$amount|$receivedAt"))
            put("received_at", receivedAt)
            put("source_package", "cl.brujula.app.manual")
            put("raw_text", rawText)
            put("amount", amount)
            put("merchant", merchant.trim())
            put("movement_type", "expense")
            put("category", category)
            put("category_manual", 1)
            put("payment_source", paymentSource)
            put("status", "captured")
        }
        return db.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun insertImportedEvent(
        receivedAt: Long,
        rawText: String,
        amount: Int?,
        merchant: String?,
        movementType: String,
        category: String,
        paymentSource: String,
        status: String
    ): Boolean {
        val db = helper.writableDatabase
        val fingerprint = sha256("import|$receivedAt|$rawText|$amount|$merchant")
        val values = ContentValues().apply {
            put("fingerprint", fingerprint)
            put("received_at", receivedAt)
            put("source_package", "cl.brujula.app.import")
            put("raw_text", rawText)
            put("amount", amount)
            put("merchant", merchant)
            put("movement_type", movementType)
            put("category", category)
            put("category_manual", 1)
            put("payment_source", paymentSource)
            put("status", status)
        }
        return db.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun insertSyncEvent(
        fingerprint: String,
        receivedAt: Long,
        sourcePackage: String,
        rawText: String,
        amount: Int?,
        merchant: String?,
        movementType: String,
        category: String,
        paymentSource: String,
        status: String,
        categoryManual: Boolean = false,
        manuallyEdited: Boolean = false
    ): Boolean {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            db.query("events", arrayOf("id", "status", "category_manual", "manually_edited", "amount", "merchant"), "fingerprint = ?", arrayOf(fingerprint), null, null, null).use { cursor ->
                if (cursor.moveToFirst()) {
                    val localId = cursor.getLong(cursor.getColumnIndexOrThrow("id"))
                    val localStatus = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                    val localManual = cursor.getInt(cursor.getColumnIndexOrThrow("category_manual")) != 0
                    val localEdited = cursor.getInt(cursor.getColumnIndexOrThrow("manually_edited")) != 0
                    val merged = ContentValues().apply {
                        if (status == "ignored" || (localStatus != "ignored" && statusRank(status) > statusRank(localStatus))) put("status", status)
                        if (!localEdited && categoryManual && !localManual) {
                            put("category", category)
                            put("category_manual", 1)
                        }
                        if (!localEdited && cursor.isNull(cursor.getColumnIndexOrThrow("amount")) && amount != null) put("amount", amount)
                        if (!localEdited && cursor.isNull(cursor.getColumnIndexOrThrow("merchant")) && !merchant.isNullOrBlank()) put("merchant", merchant)
                        if (!localEdited && manuallyEdited) {
                            put("merchant", merchant); put("amount", amount); put("received_at", receivedAt)
                            put("movement_type", movementType); put("category", category); put("category_manual", if (categoryManual) 1 else 0)
                            put("payment_source", paymentSource); put("manually_edited", 1)
                        }
                        if (status == "ignored") put("manually_edited", 1)
                    }
                    if (merged.size() > 0) db.update("events", merged, "id = ?", arrayOf(localId.toString()))
                    db.setTransactionSuccessful()
                    return false
                }
            }
            val values = ContentValues().apply {
                put("fingerprint", fingerprint)
                put("received_at", receivedAt)
                put("source_package", sourcePackage)
                put("raw_text", rawText)
                put("amount", amount)
                put("merchant", merchant)
                put("movement_type", movementType)
                put("category", category)
                put("category_manual", if (categoryManual) 1 else 0)
                put("manually_edited", if (manuallyEdited) 1 else 0)
                put("payment_source", paymentSource)
                put("status", status)
            }
            val inserted = db.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
            db.setTransactionSuccessful()
            return inserted
        } finally {
            db.endTransaction()
        }
    }
    fun insertStatementEvent(
        receivedAt: Long,
        rawText: String,
        amount: Int,
        merchant: String
    ): Boolean {
        val db = helper.writableDatabase
        val fingerprint = sha256("statement|$receivedAt|$rawText|$amount|$merchant")
        val values = ContentValues().apply {
            put("fingerprint", fingerprint)
            put("received_at", receivedAt)
            put("source_package", "cl.brujula.app.statement")
            put("raw_text", rawText)
            put("amount", amount)
            put("merchant", merchant.trim())
            put("movement_type", "expense")
            put("category", classifyCategory(merchant))
            put("category_manual", 0)
            put("payment_source", "cartola")
            put("status", "requires_review")
        }
        return db.insertWithOnConflict("events", null, values, SQLiteDatabase.CONFLICT_IGNORE) != -1L
    }

    fun reconcileStatements(): Int {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            fun readRecords(selection: String, args: Array<String>): List<ReconciliationRecord> {
                val records = mutableListOf<ReconciliationRecord>()
                db.query("events", arrayOf("id", "received_at", "amount", "merchant", "movement_type"), selection, args, null, null, "received_at ASC, id ASC").use { cursor ->
                    while (cursor.moveToNext()) {
                        val amountIndex = cursor.getColumnIndexOrThrow("amount")
                        val merchantIndex = cursor.getColumnIndexOrThrow("merchant")
                        if (!cursor.isNull(amountIndex) && !cursor.isNull(merchantIndex)) records += ReconciliationRecord(
                            cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                            cursor.getLong(cursor.getColumnIndexOrThrow("received_at")),
                            cursor.getInt(amountIndex),
                            cursor.getString(merchantIndex),
                            cursor.getString(cursor.getColumnIndexOrThrow("movement_type"))
                        )
                    }
                }
                return records
            }
            val statements = readRecords("source_package = ? AND status = ?", arrayOf("cl.brujula.app.statement", "requires_review"))
            val captured = readRecords("source_package != ? AND status IN (?, ?)", arrayOf("cl.brujula.app.statement", "captured", "confirmed"))
            val pairs = determineReconciliationPairs(statements, captured)
            pairs.forEach { (statementId, eventId) ->
                db.update("events", ContentValues().apply { put("status", "reconciled") }, "id = ?", arrayOf(eventId.toString()))
                db.update("events", ContentValues().apply { put("status", "reconciled") }, "id = ?", arrayOf(statementId.toString()))
            }
            db.setTransactionSuccessful()
            return pairs.size
        } finally {
            db.endTransaction()
        }
    }

    private fun statusRank(status: String): Int = when (status) {
        "reconciled" -> 4
        "confirmed" -> 3
        "requires_review" -> 2
        "captured" -> 1
        else -> 0
    }

    fun updateCategory(id: Long, category: String): Int {
        val values = ContentValues().apply {
            put("category", category)
            put("category_manual", 1)
        }
        return helper.writableDatabase.update("events", values, "id = ?", arrayOf(id.toString()))
    }

    fun updateMovement(event: CapturedEvent, merchant: String, amount: Int, receivedAt: Long, category: String, paymentSource: String): Int {
        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            val changes = linkedMapOf("merchant" to (event.merchant.orEmpty() to merchant.trim()), "amount" to (event.amount?.toString() to amount.toString()),
                "received_at" to (event.receivedAt.toString() to receivedAt.toString()), "category" to (event.category to category), "payment_source" to (event.paymentSource to paymentSource))
            val now = System.currentTimeMillis()
            changes.filterValues { it.first != it.second }.forEach { (field, pair) ->
                db.insert("movement_changes", null, ContentValues().apply { put("event_id", event.id); put("changed_at", now); put("field", field); put("old_value", pair.first); put("new_value", pair.second) })
            }
            val count = db.update("events", ContentValues().apply { put("merchant", merchant.trim()); put("amount", amount); put("received_at", receivedAt); put("category", category); put("category_manual", 1); put("payment_source", paymentSource); put("manually_edited", 1) }, "id = ?", arrayOf(event.id.toString()))
            db.setTransactionSuccessful()
            return count
        } finally { db.endTransaction() }
    }

    fun history(eventId: Long): List<MovementChange> {
        val result = mutableListOf<MovementChange>()
        helper.readableDatabase.query("movement_changes", null, "event_id = ?", arrayOf(eventId.toString()), null, null, "changed_at DESC, id DESC").use { cursor ->
            while (cursor.moveToNext()) result += MovementChange(cursor.getLong(cursor.getColumnIndexOrThrow("id")), eventId, cursor.getLong(cursor.getColumnIndexOrThrow("changed_at")), cursor.getString(cursor.getColumnIndexOrThrow("field")), cursor.getStringOrNull("old_value"), cursor.getStringOrNull("new_value"))
        }
        return result
    }

    fun importHistory(fingerprint: String, changes: List<MovementChange>) {
        val db = helper.writableDatabase
        val eventId = db.query("events", arrayOf("id"), "fingerprint = ?", arrayOf(fingerprint), null, null, null).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else return }
        changes.forEach { change ->
            val args = arrayOf(eventId.toString(), change.changedAt.toString(), change.field, change.oldValue, change.newValue)
            val exists = db.query("movement_changes", arrayOf("id"), "event_id = ? AND changed_at = ? AND field = ? AND old_value IS ? AND new_value IS ?", args, null, null, null).use { it.moveToFirst() }
            if (!exists) db.insert("movement_changes", null, ContentValues().apply { put("event_id", eventId); put("changed_at", change.changedAt); put("field", change.field); put("old_value", change.oldValue); put("new_value", change.newValue) })
        }
    }

    fun deleteDemoEvents(): Int =
        helper.writableDatabase.delete("events", "status = ?", arrayOf("demo"))

    fun updateStatus(id: Long, status: String): Int {
        val values = ContentValues().apply { put("status", status); if (status == "ignored") put("manually_edited", 1) }
        return helper.writableDatabase.update("events", values, "id = ?", arrayOf(id.toString()))
    }

    fun recent(limit: Int = 30, includeIgnored: Boolean = false): List<CapturedEvent> {
        val db = helper.readableDatabase
        val result = mutableListOf<CapturedEvent>()
        db.query("events", null, if (includeIgnored) null else "status != ?", if (includeIgnored) null else arrayOf("ignored"), null, null, "received_at DESC", limit.toString()).use { cursor ->
            while (cursor.moveToNext()) {
                result += CapturedEvent(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    fingerprint = cursor.getString(cursor.getColumnIndexOrThrow("fingerprint")),
                    receivedAt = cursor.getLong(cursor.getColumnIndexOrThrow("received_at")),
                    sourcePackage = cursor.getString(cursor.getColumnIndexOrThrow("source_package")),
                    rawText = cursor.getString(cursor.getColumnIndexOrThrow("raw_text")),
                    amount = cursor.getIntOrNull("amount"),
                    merchant = cursor.getStringOrNull("merchant"),
                    movementType = cursor.getStringOrNull("movement_type") ?: "expense",
                    category = cursor.getStringOrNull("category") ?: "otros",
                    paymentSource = cursor.getStringOrNull("payment_source") ?: "notificacion",
                    status = cursor.getString(cursor.getColumnIndexOrThrow("status")),
                    categoryManual = cursor.getInt(cursor.getColumnIndexOrThrow("category_manual")) != 0,
                    manuallyEdited = cursor.getInt(cursor.getColumnIndexOrThrow("manually_edited")) != 0
                )
            }
        }
        return result
    }

    private class EventDb(context: Context) : SQLiteOpenHelper(context, "finance_capture.db", null, 7) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE events (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    fingerprint TEXT NOT NULL UNIQUE,
                    received_at INTEGER NOT NULL,
                    source_package TEXT NOT NULL,
                    raw_text TEXT NOT NULL,
                    amount INTEGER,
                    merchant TEXT,
                    movement_type TEXT NOT NULL DEFAULT 'expense',
                    category TEXT NOT NULL DEFAULT 'otros',
                    category_manual INTEGER NOT NULL DEFAULT 0,
                    payment_source TEXT NOT NULL DEFAULT 'notificacion',
                    status TEXT NOT NULL
                )""".trimIndent()
            )
            db.execSQL("CREATE INDEX events_received_at ON events(received_at DESC)")
            db.execSQL("ALTER TABLE events ADD COLUMN manually_edited INTEGER NOT NULL DEFAULT 0")
            createHistoryTable(db)
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                db.execSQL("UPDATE events SET status = 'ignored' WHERE source_package NOT LIKE '%scotia%' AND source_package != 'cl.brujula.app.demo'")
                db.query("events", arrayOf("id", "raw_text"), "source_package LIKE ?", arrayOf("%scotia%"), null, null, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val values = ContentValues().apply {
                            put("amount", parseAmount(cursor.getString(cursor.getColumnIndexOrThrow("raw_text"))))
                            put("merchant", parseMerchant(cursor.getString(cursor.getColumnIndexOrThrow("raw_text"))))
                        }
                        db.update("events", values, "id = ?", arrayOf(cursor.getLong(cursor.getColumnIndexOrThrow("id")).toString()))
                    }
                }
            }
            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE events ADD COLUMN movement_type TEXT NOT NULL DEFAULT 'expense'")
                db.query("events", arrayOf("id", "raw_text"), null, null, null, null, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val values = ContentValues().apply {
                            put("movement_type", classifyMovement(cursor.getString(cursor.getColumnIndexOrThrow("raw_text"))))
                        }
                        db.update("events", values, "id = ?", arrayOf(cursor.getLong(cursor.getColumnIndexOrThrow("id")).toString()))
                    }
                }
            }
            if (oldVersion < 4) {
                db.execSQL("ALTER TABLE events ADD COLUMN category TEXT NOT NULL DEFAULT 'otros'")
                db.query("events", arrayOf("id", "raw_text"), null, null, null, null, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val values = ContentValues().apply {
                            put("category", classifyCategory(cursor.getString(cursor.getColumnIndexOrThrow("raw_text"))))
                        }
                        db.update("events", values, "id = ?", arrayOf(cursor.getLong(cursor.getColumnIndexOrThrow("id")).toString()))
                    }
                }
            }
            if (oldVersion < 5) {
                db.execSQL("ALTER TABLE events ADD COLUMN category_manual INTEGER NOT NULL DEFAULT 0")
            }
            if (oldVersion < 6) {
                db.execSQL("ALTER TABLE events ADD COLUMN payment_source TEXT NOT NULL DEFAULT 'notificacion'")
                db.query("events", arrayOf("id", "raw_text"), null, null, null, null, null).use { cursor ->
                    while (cursor.moveToNext()) {
                        val values = ContentValues().apply {
                            put("payment_source", classifyPaymentSource(cursor.getString(cursor.getColumnIndexOrThrow("raw_text"))))
                        }
                        db.update("events", values, "id = ?", arrayOf(cursor.getLong(cursor.getColumnIndexOrThrow("id")).toString()))
                    }
                }
            }
            if (oldVersion < 7) {
                db.execSQL("ALTER TABLE events ADD COLUMN manually_edited INTEGER NOT NULL DEFAULT 0")
                createHistoryTable(db)
            }
        }

        private fun createHistoryTable(db: SQLiteDatabase) {
            db.execSQL("CREATE TABLE movement_changes (id INTEGER PRIMARY KEY AUTOINCREMENT, event_id INTEGER NOT NULL, changed_at INTEGER NOT NULL, field TEXT NOT NULL, old_value TEXT, new_value TEXT, FOREIGN KEY(event_id) REFERENCES events(id))")
            db.execSQL("CREATE INDEX movement_changes_event ON movement_changes(event_id, changed_at DESC)")
        }
    }
}

private fun android.database.Cursor.getIntOrNull(column: String): Int? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getInt(index)
}

private fun android.database.Cursor.getStringOrNull(column: String): String? {
    val index = getColumnIndexOrThrow(column)
    return if (isNull(index)) null else getString(index)
}

private fun parseAmount(text: String): Int? {
    val prioritized = Regex("(?i)\\b(?:por|monto|total|cargo)\\s*(?:de|por)?\\s*:?\\s*\\$?\\s*([0-9]{1,3}(?:[.][0-9]{3})+|[0-9]{4,})")
    val match = prioritized.find(text) ?: Regex("\\$\\s*([0-9]{1,3}(?:[.][0-9]{3})+|[0-9]{4,})").find(text) ?: return null
    return match.groupValues[1].replace(".", "").toIntOrNull()
}

private fun parseMerchant(text: String): String? {
    val cleaned = text.replace("\\s+".toRegex(), " ").trim()
    val afterAmount = Regex("(?i)\\bpor\\s+\\$?\\s*[0-9]{1,3}(?:[.][0-9]{3})+\\s+en\\s+([^·.]+)").find(cleaned)?.groupValues?.get(1)
    val afterEn = Regex("(?i)\\ben\\s+([^·.]+)").find(cleaned)?.groupValues?.get(1)
    return (afterAmount ?: afterEn ?: cleaned)
        .replace(Regex("(?i)\\s+si\\s+desconoces.*$"), "")
        .replace(Regex("\\s+por\\s+\\$?[0-9.]+.*$"), "")
        .trim()
        .takeIf { it.isNotBlank() }
        ?.take(160)
}


private fun classifyMovement(text: String): String {
    val normalized = text.lowercase(Locale.ROOT)
    return when {
        Regex("\\b(pago|pagaste|pagó)\\b.*\\b(tarjeta|tc|cr[eé]dito)\\b|\\b(tarjeta|tc|cr[eé]dito)\\b.*\\b(pago|pagaste|pagó)\\b").containsMatchIn(normalized) -> "card_payment"
        listOf("devolución", "devolucion", "reembolso", "reversa", "reverso", "devuelto").any(normalized::contains) -> "refund"
        listOf("transferencia", "traspaso", "depósito", "deposito").any(normalized::contains) -> "transfer"
        else -> "expense"
    }
}


private fun classifyCategory(text: String): String {
    val normalized = text.lowercase(Locale.ROOT)
    return when {
        listOf("jumbo", "lider", "unimarc", "supermercado", "mercado", "verduleria", "verdulería", "panaderia", "panadería").any(normalized::contains) -> "alimentacion"
        listOf("farmacia", "cruz verde", "salcobrand", "ahumada", "clinica", "clínica", "hospital", "dentista", "salud").any(normalized::contains) -> "salud"
        listOf("arriendo", "alquiler", "condominio", "inmobiliaria", "muebles", "ferreteria", "ferretería").any(normalized::contains) -> "vivienda"
        listOf("luz", "agua", "gas", "internet", "telefono", "teléfono", "netflix", "spotify", "youtube", "suscripcion", "suscripción", "verisure").any(normalized::contains) -> "servicios"
        listOf("uber", "recorrido", "taxi", "cabify", "transantiago", "metro", "bencina", "gasolina", "copec", "shell", "auto", "estacionamiento", "peaje").any(normalized::contains) -> "transporte"
        listOf("restaurante", "restaurant", "cafe", "café", "bar", "cine", "teatro", "salida", "concierto", "viaje", "hotel").any(normalized::contains) -> "ocio"
        listOf("falabella", "paris", "ripley", "mall", "shopping", "tienda", "ropa", "zapateria", "zapatería", "amazon", "mercadolibre").any(normalized::contains) -> "compras"
        else -> "otros"
    }
}

private fun classifyPaymentSource(text: String): String {
    val normalized = text.lowercase(Locale.ROOT)
    return when {
        listOf("tarjeta de crédito", "tarjeta de credito", "tarjeta", "tc").any(normalized::contains) -> "tarjeta_credito"
        listOf("cuenta corriente", "cuenta corriente", "cuenta").any(normalized::contains) -> "cuenta_corriente"
        normalized.startsWith("manual") -> "manual"
        else -> "notificacion"
    }
}

private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
