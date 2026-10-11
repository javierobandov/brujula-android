package cl.brujula.app

import android.os.Bundle
import android.content.Intent
import android.content.Context
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay

private val Green = Color(0xFF237F6D)
private val Paper = Color(0xFFF6F8F5)
private val Ink = Color(0xFF1F2A27)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { BrujulaApp(this) } }
}

@Composable fun BrujulaApp(activity: MainActivity) {
    var syncing by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    val updateChecker = remember { UpdateChecker(activity) }
    var captured by remember { mutableStateOf(CapturedEventStore(activity).recent()) }
    var editingEvent by remember { mutableStateOf<CapturedEvent?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var statementStatus by remember { mutableStateOf<String?>(null) }
    var monthlyBudget by remember { mutableStateOf(BudgetSettings(activity).monthlyBudget()) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    val statementLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val csv = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val store = CapturedEventStore(activity)
        val imported = importStatementCsv(activity, csv)
        val reconciled = store.reconcileStatements()
        captured = store.recent()
        statementStatus = if (imported == 0) "No se reconocieron movimientos de cartola." else "Cartola: $imported movimientos importados; $reconciled conciliados automáticamente."
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val csv = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val imported = importCapturedCsv(activity, csv)
        captured = CapturedEventStore(activity).recent()
        importStatus = if (imported == 0) "No se encontraron movimientos nuevos en el CSV." else "Se importaron $imported movimientos; quedan pendientes de revisión."
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        activity.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(capturedCsv(captured))
        }
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            captured = CapturedEventStore(activity).recent()
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                captured = CapturedEventStore(activity).recent()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val realCaptured = captured.filter { it.status in setOf("captured", "confirmed", "reconciled") && it.amount != null }
    val pendingReconciliation = captured.count { it.status == "requires_review" }
    val reconciledCount = captured.count { it.status == "reconciled" }
    val expenses = realCaptured.filter { it.movementType == "expense" }
    val monthExpenses = expenses.filter { it.receivedAt.isCurrentMonth() }
    val total = monthExpenses.sumOf { it.amount ?: 0 }
    val categoryTotals = listOf("alimentacion", "transporte", "suscripciones", "salud", "otros")
        .map { category -> category to monthExpenses.filter { it.category == category }.sumOf { it.amount ?: 0 } }
    val paymentTotals = listOf("tarjeta_credito", "cuenta_corriente", "manual", "cartola")
        .map { source -> source to monthExpenses.filter { it.paymentSource == source }.sumOf { it.amount ?: 0 } }
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Paper)) {
        Surface(color = Paper, modifier = Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Text("BRÚJULA", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp); Text("Tus gastos claros.", color = Ink, fontSize = 31.sp, fontWeight = FontWeight.Bold); Text("Captura local en prueba · sin sincronización bancaria", color = Color.Gray, fontSize = 14.sp); Text("La bandeja se actualiza al volver a la app y periódicamente mientras está abierta.", color = Color.Gray, fontSize = 12.sp) }
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(20.dp)) { Text("Gasto del mes", color = Color.Gray, fontSize = 13.sp); Text(clp(total), color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(14.dp)); LinearProgressIndicator(progress = { (total.toFloat() / monthlyBudget.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Green, trackColor = Color(0xFFE8EEE9)); Spacer(Modifier.height(8.dp)); Text("${percent(total, monthlyBudget)}% del presupuesto utilizado", color = Green, fontSize = 12.sp); Text("Presupuesto mensual: ${clp(monthlyBudget)}", color = Color.Gray, fontSize = 12.sp); Text("Conciliación: $reconciledCount conciliados · $pendingReconciliation por revisar", color = if (pendingReconciliation > 0) Color(0xFF9A6500) else Green, fontSize = 12.sp) }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Distribución del mes", color = Ink, fontWeight = FontWeight.Bold)
                            categoryTotals.filter { it.second > 0 }.forEach { (category, amount) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(category.replaceFirstChar { it.uppercase() }, color = Color.Gray, fontSize = 12.sp)
                                    Text(clp(amount), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            Text("Por medio de pago", color = Ink, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp))
                            paymentTotals.filter { it.second > 0 }.forEach { (source, amount) ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(source.replace("_", " ").replaceFirstChar { it.uppercase() }, color = Color.Gray, fontSize = 12.sp)
                                    Text(clp(amount), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                }
                            }
                            if (total == 0) Text("Aún no hay gastos reales en el mes actual.", color = Color.Gray, fontSize = 12.sp)
                        }
                    }
                    Button(onClick = { syncing = true; captured = CapturedEventStore(activity).recent(); syncing = false }, modifier = Modifier.fillMaxWidth(), enabled = !syncing) { Text(if (syncing) "Actualizando…" else "Actualizar resumen") }
                    OutlinedButton(onClick = { exportLauncher.launch("brujula-respaldo-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.csv") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Exportar respaldo CSV completo")
                    }
                    OutlinedButton(onClick = {
                        importLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/*"))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Importar respaldo CSV")
                    }
                    OutlinedButton(onClick = {
                        statementLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/*"))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Importar cartola CSV")
                    }
                    importStatus?.let { Text(it, color = Color.Gray, fontSize = 12.sp) }
                    statementStatus?.let { Text(it, color = Color.Gray, fontSize = 12.sp) }
                    OutlinedButton(onClick = {
                        updateStatus = "Buscando actualización…"
                        updateChecker.check { result ->
                            result.onSuccess { update ->
                                availableUpdate = update
                                updateStatus = if (update == null || update.version == BuildConfig.VERSION_NAME) "Ya tienes la última versión." else "Disponible Brújula ${update.version}."
                            }.onFailure { updateStatus = "No se pudo consultar GitHub: ${it.message ?: "error de red"}" }
                        }
                    }, modifier = Modifier.fillMaxWidth()) { Text("Buscar actualización") }
                    if (availableUpdate != null && availableUpdate!!.version != BuildConfig.VERSION_NAME) {
                        Button(onClick = { updateChecker.downloadAndInstall(availableUpdate!!, onStatus = { updateStatus = it }) }, modifier = Modifier.fillMaxWidth()) {
                            Text("Descargar Brújula ${availableUpdate!!.version}")
                        }
                    }
                    updateStatus?.let { Text(it, color = Color.Gray, fontSize = 12.sp) }
                }
                item {
                    OutlinedButton(onClick = { showBudgetDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Configurar presupuesto mensual")
                    }
                    OutlinedButton(onClick = { showManualDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text("Agregar gasto manual")
                    }
                    OutlinedButton(onClick = { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Configurar captura de notificaciones")
                    }
                    OutlinedButton(onClick = {
                        CapturedEventStore(activity).insertDemoEvent()
                        captured = CapturedEventStore(activity).recent()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Insertar evento de prueba")
                    }
                    OutlinedButton(onClick = {
                        CapturedEventStore(activity).insertClassificationDemoEvents()
                        captured = CapturedEventStore(activity).recent()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Probar clasificación de movimientos")
                    }
                    OutlinedButton(onClick = {
                        CapturedEventStore(activity).deleteDemoEvents()
                        captured = CapturedEventStore(activity).recent()
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Limpiar eventos DEMO")
                    }
                    Text("La app sólo guardará notificaciones filtradas de movimientos; no usa credenciales bancarias.", color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Bandeja capturada", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                        TextButton(onClick = { captured = CapturedEventStore(activity).recent() }) { Text("Actualizar") }
                    }
                }
                if (captured.isEmpty()) {
                    item { Text("Todavía no hay eventos. Puedes insertar una prueba local o esperar una notificación real.", color = Color.Gray, fontSize = 13.sp) }
                } else {
                    items(captured) { event ->
                        CapturedEventRow(event) {
                            CapturedEventStore(activity).updateStatus(event.id, "confirmed")
                            captured = CapturedEventStore(activity).recent()
                        }
                    }
                }
                item { Text("Movimientos reales capturados", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                if (realCaptured.isEmpty()) {
                    item { Text("Aún no hay movimientos reales. Los eventos DEMO no se incluyen en el resumen.", color = Color.Gray, fontSize = 13.sp) }
                } else {
                    items(realCaptured) { event ->
                        CapturedExpenseRow(
                            event = event,
                            onEditCategory = { editingEvent = event },
                            onConfirm = {
                                CapturedEventStore(activity).updateStatus(event.id, "confirmed")
                                captured = CapturedEventStore(activity).recent()
                            }
                        )
                    }
                }
            }
        }
        if (showManualDialog) {
            var manualMerchant by remember { mutableStateOf("") }
            var manualAmount by remember { mutableStateOf("") }
            var manualCategory by remember { mutableStateOf("otros") }
            var manualSource by remember { mutableStateOf("cuenta_corriente") }
            var manualError by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { showManualDialog = false },
                title = { Text("Agregar gasto manual") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualMerchant,
                            onValueChange = { manualMerchant = it },
                            label = { Text("Comercio o descripción") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = manualAmount,
                            onValueChange = { manualAmount = it },
                            label = { Text("Monto en pesos") },
                            singleLine = true
                        )
                        Text("Medio de pago", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("cuenta_corriente" to "Cuenta corriente", "tarjeta_credito" to "Tarjeta").forEach { (value, label) ->
                                FilterChip(
                                    selected = manualSource == value,
                                    onClick = { manualSource = value },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Text("Categoría", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("alimentacion" to "Alim.", "transporte" to "Transp.", "salud" to "Salud", "otros" to "Otros").forEach { (value, label) ->
                                FilterChip(
                                    selected = manualCategory == value,
                                    onClick = { manualCategory = value },
                                    label = { Text(label) }
                                )
                            }
                        }
                        manualError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val amount = parseClpInput(manualAmount)
                        when {
                            manualMerchant.isBlank() -> manualError = "Escribe una descripción."
                            amount == null || amount <= 0 -> manualError = "Ingresa un monto válido."
                            else -> {
                                CapturedEventStore(activity).insertManualExpense(
                                    merchant = manualMerchant,
                                    amount = amount,
                                    category = manualCategory,
                                    paymentSource = manualSource
                                )
                                captured = CapturedEventStore(activity).recent()
                                showManualDialog = false
                            }
                        }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { showManualDialog = false }) { Text("Cancelar") } }
            )
        }
        if (showBudgetDialog) {
            var budgetInput by remember { mutableStateOf(monthlyBudget.toString()) }
            var budgetError by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { showBudgetDialog = false },
                title = { Text("Presupuesto mensual") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = budgetInput,
                            onValueChange = { budgetInput = it },
                            label = { Text("Monto en pesos") },
                            singleLine = true
                        )
                        Text("Se usa sólo para calcular el porcentaje del mes actual.", color = Color.Gray, fontSize = 12.sp)
                        budgetError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val value = parseClpInput(budgetInput)
                        if (value == null || value <= 0) {
                            budgetError = "Ingresa un presupuesto mayor que cero."
                        } else {
                            BudgetSettings(activity).setMonthlyBudget(value)
                            monthlyBudget = value
                            showBudgetDialog = false
                        }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { showBudgetDialog = false }) { Text("Cancelar") } }
            )
        }
        editingEvent?.let { event ->
            AlertDialog(
                onDismissRequest = { editingEvent = null },
                title = { Text("Categoría del movimiento") },
                text = {
                    Column {
                        listOf("alimentacion", "transporte", "suscripciones", "salud", "otros").forEach { category ->
                            TextButton(onClick = {
                                CapturedEventStore(activity).updateCategory(event.id, category)
                                captured = CapturedEventStore(activity).recent()
                                editingEvent = null
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text(category.replaceFirstChar { it.uppercase() })
                            }
                        }
                    }
                },
                confirmButton = { TextButton(onClick = { editingEvent = null }) { Text("Cancelar") } }
            )
        }
    }
}

@Composable private fun CapturedEventRow(event: CapturedEvent, onReview: () -> Unit = {}) {
    Card(shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6F1))) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Text(event.merchant ?: "Notificación sin comercio", color = Ink, fontWeight = FontWeight.SemiBold)
            Text(event.rawText, color = Color.Gray, fontSize = 12.sp, maxLines = 3)
            Text("${event.statusLabel()} · ${event.receivedAtLabel()}${event.amount?.let { " · ${clp(it)}" } ?: ""}", color = Green, fontSize = 12.sp)
            if (event.status == "requires_review") TextButton(onClick = onReview) { Text("Marcar como revisado") }
        }
    }
}

@Composable private fun CapturedExpenseRow(event: CapturedEvent, onEditCategory: () -> Unit, onConfirm: () -> Unit) {
    Card(shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(event.merchant ?: "Comercio pendiente", color = Ink, fontWeight = FontWeight.SemiBold); Text("${event.categoryLabel()} · ${event.movementTypeLabel()} · ${event.paymentSourceLabel()} · ${event.receivedAtShortLabel()}", color = Color.Gray, fontSize = 12.sp) }
            Column(horizontalAlignment = Alignment.End) {
                Text(clp(event.amount ?: 0), color = Ink, fontWeight = FontWeight.Bold)
                if (event.status == "captured") TextButton(onClick = onConfirm) { Text("Confirmar movimiento") }
                TextButton(onClick = onEditCategory) { Text("Cambiar categoría") }
            }
        }
    }
}


private class BudgetSettings(context: Context) {
    private val preferences = context.getSharedPreferences("brujula_settings", Context.MODE_PRIVATE)

    fun monthlyBudget(): Int = preferences.getInt("monthly_budget", 1_000_000)

    fun setMonthlyBudget(value: Int) {
        preferences.edit().putInt("monthly_budget", value).apply()
    }
}
private fun clp(amount: Int) = NumberFormat.getCurrencyInstance(Locale("es", "CL")).apply { maximumFractionDigits = 0 }.format(amount)
private fun percent(amount: Int, budget: Int) = (amount.toFloat() / budget.coerceAtLeast(1) * 100).toInt()
private fun CapturedEvent.statusLabel() = when (status) {
    "demo" -> "DEMO"
    "confirmed" -> "CONFIRMADO"
    "reconciled" -> "CONCILIADO"
    "requires_review" -> "REQUIERE_REVISION"
    else -> "CAPTURADO"
}
private fun CapturedEvent.receivedAtLabel() = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("es", "CL")).format(Date(receivedAt))
private fun CapturedEvent.receivedAtShortLabel() = SimpleDateFormat("dd MMM, HH:mm", Locale("es", "CL")).format(Date(receivedAt))
private fun Long.isCurrentMonth(): Boolean {
    val event = Calendar.getInstance().apply { timeInMillis = this@isCurrentMonth }
    val now = Calendar.getInstance()
    return event.get(Calendar.YEAR) == now.get(Calendar.YEAR) && event.get(Calendar.MONTH) == now.get(Calendar.MONTH)
}

private fun capturedCsv(events: List<CapturedEvent>): String = buildString {
    appendLine("fecha_hora,estado,tipo_movimiento,categoria,medio_pago,comercio,monto,paquete_origen,texto_original")
    events.forEach { event ->
        appendLine(listOf(
            event.receivedAtLabel(),
            event.statusLabel(),
            event.movementTypeLabel(),
            event.categoryLabel(),
            event.paymentSourceLabel(),
            event.merchant.orEmpty(),
            event.amount?.toString().orEmpty(),
            event.sourcePackage,
            event.rawText
        ).joinToString(",") { csvCell(it) })
    }
}

private fun CapturedEvent.movementTypeLabel() = when (movementType) {
    "card_payment" -> "PAGO_TARJETA"
    "refund" -> "DEVOLUCION"
    "transfer" -> "TRANSFERENCIA"
    else -> "GASTO"
}


private fun CapturedEvent.paymentSourceLabel() = when (paymentSource) {
    "tarjeta_credito" -> "TARJETA"
    "cuenta_corriente" -> "CUENTA_CORRIENTE"
    "manual" -> "MANUAL"
    else -> "NOTIFICACION"
}

private fun parseClpInput(value: String): Int? =
    value.trim().replace(".", "").replace(",", ".").toDoubleOrNull()?.toInt()

private fun CapturedEvent.categoryLabel() = when (category) {
    "alimentacion" -> "ALIMENTACION"
    "transporte" -> "TRANSPORTE"
    "suscripciones" -> "SUSCRIPCIONES"
    "salud" -> "SALUD"
    else -> "OTROS"
}

private fun importStatementCsv(activity: MainActivity, csv: String): Int {
    val rows = parseCsvRows(csv)
    if (rows.size < 2) return 0
    val header = rows.first().map { it.trim().lowercase(Locale.ROOT) }
    fun indexOf(names: List<String>) = header.indexOfFirst { value -> names.any { name -> value.contains(name) } }
    val dateIndex = indexOf(listOf("fecha"))
    val merchantIndex = indexOf(listOf("comercio", "descrip", "detalle", "glosa", "concepto", "merchant"))
    val amountIndex = indexOf(listOf("monto", "amount", "cargo", "debe", "debit"))
    if (dateIndex < 0 || merchantIndex < 0 || amountIndex < 0) return 0
    val store = CapturedEventStore(activity)
    return rows.drop(1).count { row ->
        if (row.size <= maxOf(dateIndex, merchantIndex, amountIndex)) return@count false
        val amount = row[amountIndex].replace(Regex("[^0-9-]"), "").toIntOrNull() ?: return@count false
        if (amount <= 0) return@count false
        val merchant = row[merchantIndex].trim()
        if (merchant.isBlank()) return@count false
        val receivedAt = parseStatementDate(row[dateIndex]) ?: System.currentTimeMillis()
        store.insertStatementEvent(
            receivedAt = receivedAt,
            rawText = row.joinToString(" | "),
            amount = amount,
            merchant = merchant
        )
    }
}

private fun parseStatementDate(value: String): Long? {
    val patterns = listOf("dd/MM/yyyy", "dd-MM-yyyy", "yyyy-MM-dd", "dd/MM/yyyy HH:mm:ss", "dd-MM-yyyy HH:mm:ss")
    return patterns.firstNotNullOfOrNull { pattern ->
        runCatching { SimpleDateFormat(pattern, Locale("es", "CL")).parse(value.trim())?.time }.getOrNull()
    }
}

private fun importCapturedCsv(activity: MainActivity, csv: String): Int {
    val rows = parseCsvRows(csv)
    if (rows.size < 2) return 0
    val header = rows.first().map { it.trim() }
    fun indexOf(name: String) = header.indexOf(name)
    val dateIndex = indexOf("fecha_hora")
    val statusIndex = indexOf("estado")
    val typeIndex = indexOf("tipo_movimiento")
    val categoryIndex = indexOf("categoria")
    val paymentIndex = indexOf("medio_pago")
    val merchantIndex = indexOf("comercio")
    val amountIndex = indexOf("monto")
    val rawIndex = indexOf("texto_original")
    if (listOf(dateIndex, statusIndex, typeIndex, categoryIndex, paymentIndex, merchantIndex, amountIndex, rawIndex).any { it < 0 }) return 0
    val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("es", "CL"))
    val store = CapturedEventStore(activity)
    return rows.drop(1).count { row ->
        val indexes = listOf(dateIndex, statusIndex, typeIndex, categoryIndex, paymentIndex, merchantIndex, amountIndex, rawIndex)
        if (row.size <= indexes.max()) return@count false
        val statusLabel = row[statusIndex]
        if (statusLabel == "DEMO") return@count false
        val rawText = row[rawIndex]
        val merchant = row[merchantIndex].takeIf { it.isNotBlank() }
        val amount = row[amountIndex].replace(Regex("[^0-9-]"), "").toIntOrNull()
        val receivedAt = dateFormat.parse(row[dateIndex])?.time ?: System.currentTimeMillis()
        store.insertImportedEvent(
            receivedAt = receivedAt,
            rawText = rawText,
            amount = amount,
            merchant = merchant,
            movementType = row[typeIndex].toMovementType(),
            category = row[categoryIndex].toCategory(),
            paymentSource = row[paymentIndex].toPaymentSource(),
            status = if (statusLabel == "CONFIRMADO") "confirmed" else "captured"
        )
    }
}

private fun parseCsvRows(csv: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    val row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var index = 0
    while (index < csv.length) {
        val char = csv[index]
        when {
            char.code == 34 && quoted && index + 1 < csv.length && csv[index + 1].code == 34 -> { field.append(char); index++ }
            char.code == 34 -> quoted = !quoted
            char.code == 44 && !quoted -> { row += field.toString(); field.clear() }
            (char.code == 10 || char.code == 13) && !quoted -> {
                if (char.code == 13 && index + 1 < csv.length && csv[index + 1].code == 10) index++
                row += field.toString(); field.clear()
                if (row.any { it.isNotBlank() }) rows += row.toList()
                row.clear()
            }
            else -> field.append(char)
        }
        index++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row.toList() }
    return rows
}

private fun String.toMovementType() = when (this) {
    "PAGO_TARJETA" -> "card_payment"
    "DEVOLUCION" -> "refund"
    "TRANSFERENCIA" -> "transfer"
    else -> "expense"
}

private fun String.toCategory() = when (this) {
    "ALIMENTACION" -> "alimentacion"
    "TRANSPORTE" -> "transporte"
    "SUSCRIPCIONES" -> "suscripciones"
    "SALUD" -> "salud"
    else -> "otros"
}

private fun String.toPaymentSource() = when (this) {
    "TARJETA" -> "tarjeta_credito"
    "CUENTA_CORRIENTE" -> "cuenta_corriente"
    "MANUAL" -> "manual"
    else -> "notificacion"
}

private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""