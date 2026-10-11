package cl.brujula.app

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.security.MessageDigest
import java.util.Locale

data class CapturedEvent(
    val id: Long,
    val receivedAt: Long,
    val sourcePackage: String,
    val rawText: String,
    val amount: Int?,
    val merchant: String?,
    val movementType: String,
    val status: String
)

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

    fun recent(limit: Int = 30): List<CapturedEvent> {
        val db = helper.readableDatabase
        val result = mutableListOf<CapturedEvent>()
        db.query("events", null, "status != ?", arrayOf("ignored"), null, null, "received_at DESC", limit.toString()).use { cursor ->
            while (cursor.moveToNext()) {
                result += CapturedEvent(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                    receivedAt = cursor.getLong(cursor.getColumnIndexOrThrow("received_at")),
                    sourcePackage = cursor.getString(cursor.getColumnIndexOrThrow("source_package")),
                    rawText = cursor.getString(cursor.getColumnIndexOrThrow("raw_text")),
                    amount = cursor.getIntOrNull("amount"),
                    merchant = cursor.getStringOrNull("merchant"),
                    movementType = cursor.getStringOrNull("movement_type") ?: "expense",
                    status = cursor.getString(cursor.getColumnIndexOrThrow("status"))
                )
            }
        }
        return result
    }

    private class EventDb(context: Context) : SQLiteOpenHelper(context, "finance_capture.db", null, 3) {
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
                    status TEXT NOT NULL
                )""".trimIndent()
            )
            db.execSQL("CREATE INDEX events_received_at ON events(received_at DESC)")
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

private fun sha256(value: String) = MessageDigest.getInstance("SHA-256")
    .digest(value.toByteArray())
    .joinToString("") { "%02x".format(it) }
