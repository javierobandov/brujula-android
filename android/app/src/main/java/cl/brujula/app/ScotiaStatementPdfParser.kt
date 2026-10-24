package cl.brujula.app

import java.util.Calendar
import java.util.Locale

/** A statement row contains only normalized fields; source lines and account identifiers are discarded. */
internal data class ScotiaStatementMovement(
    val receivedAt: Long,
    val merchant: String,
    val amount: Int,
    val movementType: String,
    val category: String,
    val paymentSource: String,
    val occurrence: Int
)

/** Parses the text layer of Scotiabank Chile's credit-card statement detail table. */
internal object ScotiaStatementPdfParser {
    private val moneyPattern = Regex("\\$\\s*(-?[0-9][0-9.]*)")
    private val datePattern = Regex("(?<![0-9])([0-3]?[0-9])[/.-]([01]?[0-9])(?:[/.-]([0-9]{2,4}))?(?![0-9])|(?<![0-9])([0-3][0-9])([01][0-9])(?![0-9])")
    private val ignored = listOf(
        "fecha estado de cuenta", "total operaciones", "pago en efectivo", "pagos a la cuenta",
        "compras en cuotas a la cuenta", "informacion de pago", "información de pago",
        "monto total facturado", "cupo total", "cargo del mes", "valor cuota", "total credito",
        "total crédito", "comprobante de pago", "cargos, comisiones, impuestos y abono"
    )

    fun parse(text: String, statementYear: Int): List<ScotiaStatementMovement> {
        val detailStart = text.indexOf("II. DETALLE", ignoreCase = true)
        val paymentInfoStart = text.indexOf("III. INFORMACIÓN DE PAGO", ignoreCase = true)
            .takeIf { it >= 0 } ?: text.indexOf("III. INFORMACION DE PAGO", ignoreCase = true)
        val detailText = if (detailStart >= 0 && paymentInfoStart > detailStart) {
            text.substring(detailStart, paymentInfoStart)
        } else text
        val result = mutableListOf<ScotiaStatementMovement>()
        val occurrences = mutableMapOf<String, Int>()
        var pending = StringBuilder()
        var currentDate: Long? = null
        for (rawLine in detailText.lineSequence()) {
            val line = rawLine.replace(Regex("\\s+"), " ").trim()
            if (line.isBlank()) continue
            val lower = line.lowercase(Locale.ROOT)
            val firstMoney = line.indexOf('$').let { if (it < 0) line.length else it }
            val date = datePattern.findAll(line).firstOrNull { it.range.first < firstMoney }
            if (ignored.any(lower::contains) && date == null) { pending = StringBuilder(); continue }
            if (date != null) {
                val parts = date.groupValues
                val day = (parts[1].ifBlank { parts[4] }).toIntOrNull()
                val month = (parts[2].ifBlank { parts[5] }).toIntOrNull()
                val yearInText = parts[3].toIntOrNull()
                if (day != null && month != null && day in 1..31 && month in 1..12) {
                    currentDate = dateMillis(day, month, yearInText?.let { if (it < 100) 2000 + it else it } ?: statementYear)
                }
            }

            if (moneyPattern.containsMatchIn(line)) {
                val dateForRow = currentDate
                val amounts = moneyPattern.findAll(line).mapNotNull { it.groupValues[1].replace(".", "").toIntOrNull() }.toList()
                val description = cleanDescription(buildString {
                    append(pending).append(' ')
                    append(line.substringBefore('$'))
                })
                pending = StringBuilder()
                if (dateForRow == null || amounts.isEmpty() || description.length < 2) continue
                val normalizedDescription = description.lowercase(Locale.ROOT)
                if (ignored.any(normalizedDescription::contains)) continue

                // In Scotiabank's detail table, amount 2 is Cargo del Mes; amount 1 is total purchase.
                val amount = kotlin.math.abs(if (amounts.size >= 2) amounts[1] else amounts[0])
                if (amount <= 0) continue
                val type = movementType(normalizedDescription)
                val movement = ScotiaStatementMovement(
                    receivedAt = dateForRow,
                    merchant = description,
                    amount = amount,
                    movementType = type,
                    category = if (type == "expense") classifyStatementCategory(description) else "otros",
                    paymentSource = if (type == "card_payment") "cuenta_corriente" else "tarjeta_credito",
                    occurrence = 0
                )
                val key = listOf(movement.receivedAt, movement.merchant.lowercase(Locale.ROOT), movement.amount, movement.movementType).joinToString("|")
                val occurrence = (occurrences[key] ?: 0) + 1
                occurrences[key] = occurrence
                result += movement.copy(occurrence = occurrence)
            } else if (currentDate != null && !looksLikeHeader(lower)) {
                // PDF text extractors often place date, merchant and amount columns on separate lines.
                pending.append(' ').append(line)
            }
        }
        return result
    }

    private fun dateMillis(day: Int, month: Int, year: Int): Long? = runCatching {
        val calendar = Calendar.getInstance().apply {
            clear()
            isLenient = false
            set(year, month - 1, day, 12, 0, 0)
        }
        calendar.timeInMillis
    }.getOrNull()

    private fun cleanDescription(value: String): String = value
        .replace(datePattern, " ")
        .replace(moneyPattern, " ")
        .replace(Regex("\\b[0-9]{4,}[A-Z]?\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\b(?:SANTIAGO|PENCO|CONCEPCION|CONCEPCIÓN|CHILE|CL)\\b", RegexOption.IGNORE_CASE), " ")
        .replace(Regex("\\s+"), " ")
        .trim(' ', '|', '-', ':', '.')
        .take(100)

    private fun looksLikeHeader(value: String) = listOf("ii. detalle", "fecha", "comercio", "descripción", "descripcion", "detalle", "total", "cuota", "tasa int").any(value::contains)

    private fun movementType(description: String): String = when {
        listOf("devolucion", "devolución", "reembolso", "reversa", "abono", "credito por").any(description::contains) -> "refund"
        listOf("pago tarjeta", "pago tc", "pago credito", "pago crédito", "pago recibido", "pago en efectivo", "pagos a la cuenta").any(description::contains) -> "card_payment"
        listOf("avance efectivo", "avance en cuotas", "giro cajero", "avance cajero").any(description::contains) -> "transfer"
        else -> "expense"
    }

    private fun classifyStatementCategory(description: String): String {
        val text = description.lowercase(Locale.ROOT)
        return when {
            listOf("jumbo", "lider", "líder", "unimarc", "supermercado", "mercado").any(text::contains) -> "alimentacion"
            listOf("farmacia", "clinica", "clínica", "salud", "hospital").any(text::contains) -> "salud"
            listOf("netflix", "spotify", "microsoft", "google", "suscripcion").any(text::contains) -> "servicios"
            listOf("uber", "copec", "shell", "estacionamiento", "tag ", "recorrido").any(text::contains) -> "transporte"
            listOf("restaurante", "cafe", "café", "cine", "hotel", "viaje").any(text::contains) -> "ocio"
            else -> "otros"
        }
    }
}
