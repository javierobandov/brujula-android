package cl.brujula.app

import android.app.DatePickerDialog
import android.content.Context
import java.time.LocalDate
import java.time.ZoneId

internal fun parseDateInput(value: String): Long? = runCatching {
    require(Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(value))
    LocalDate.parse(value).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}.getOrNull()

internal fun chooseDate(context: Context, value: String, onSelected: (String) -> Unit) {
    val date = runCatching { LocalDate.parse(value) }.getOrDefault(LocalDate.now())
    DatePickerDialog(context, { _, year, month, day ->
        onSelected(LocalDate.of(year, month + 1, day).toString())
    }, date.year, date.monthValue - 1, date.dayOfMonth).show()
}

/** Whole Chilean pesos only; invalid separators, negatives and overflow never become another amount. */
internal fun parseClpInput(value: String): Int? {
    val input = value.trim().removePrefix("$").trim()
    if (!Regex("(?:[0-9]+|[0-9]{1,3}(?:\\.[0-9]{3})+)").matches(input)) return null
    return input.replace(".", "").toIntOrNull()
}
