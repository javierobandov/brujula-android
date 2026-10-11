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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import org.json.JSONArray
import org.json.JSONObject
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
    val firebaseSync = remember { FirebaseCloudSync(activity) }
    var cloudAccountLabel by remember { mutableStateOf(firebaseSync.currentUserEmail() ?: "Sin cuenta cloud") }
    var showCloudAccountDialog by remember { mutableStateOf(false) }
    var captured by remember { mutableStateOf(CapturedEventStore(activity).recent()) }
    var editingEvent by remember { mutableStateOf<CapturedEvent?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var statementStatus by remember { mutableStateOf<String?>(null) }
    var monthlyBudget by remember { mutableStateOf(BudgetSettings(activity).monthlyBudget()) }
    var showBudgetDialog by remember { mutableStateOf(false) }
    var showOperations by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showIncomeDialog by remember { mutableStateOf(false) }
    var showCommitmentDialog by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var planningVersion by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableStateOf(0) }
    var movementFilter by remember { mutableStateOf("Todos") }
    var movementQuery by remember { mutableStateOf("") }
    var movementPayment by remember { mutableStateOf("Todos") }
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
            writer.write(capturedCsv(CapturedEventStore(activity).recent(10_000)))
        }
    }
    var syncStatus by remember { mutableStateOf<String?>(null) }
    val syncExportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        activity.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(syncBundleJson(CapturedEventStore(activity).recent(10_000), PlanningStore(activity).exportJson()))
        }
        syncStatus = "Paquete de sincronización exportado."
    }
    val syncImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val json = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val imported = importSyncBundle(activity, json)
        captured = CapturedEventStore(activity).recent()
        syncStatus = if (imported == 0) "No se encontraron movimientos nuevos en el paquete." else "Se incorporaron $imported movimientos nuevos."
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
    val categoryTotals = listOf("servicios", "casa", "supermercado", "salidas", "auto", "otros")
        .map { category -> category to monthExpenses.filter { it.category == category }.sumOf { it.amount ?: 0 } }
    val paymentTotals = listOf("tarjeta_credito", "cuenta_corriente", "manual", "cartola")
        .map { source -> source to monthExpenses.filter { it.paymentSource == source }.sumOf { it.amount ?: 0 } }
    val planningStore = remember { PlanningStore(activity) }
    val plannedIncomeTotal = planningStore.incomes().sumOf { it.amount }
    val activeCommitments = planningStore.commitments().filter { it.isActive }
    val plannedCommitmentTotal = activeCommitments.sumOf { it.amount }
    val monthlyGoal = planningStore.monthlyGoal()
    val projections = planningStore.projections()
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Paper)) {
        Surface(color = Paper, modifier = Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item {
                    TabRow(selectedTabIndex = selectedTab, containerColor = Color.Transparent) {
                        listOf("Resumen", "Movimientos", "Captura", "Planificación", "Cuenta").forEachIndexed { index, label ->
                            Tab(selected = selectedTab == index, onClick = { selectedTab = index }, text = { Text(label, fontSize = 11.sp) })
                        }
                    }
                }
                if (selectedTab == 0) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Column {
                            Text("BRÚJULA", color = Green, fontSize = 16.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                            Text("Octubre 2026", color = Color.Gray, fontSize = 13.sp)
                        }
                        Text("●", color = Green, fontSize = 26.sp)
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Gasto del mes", color = Color.Gray, fontSize = 14.sp)
                            Text(clp(total), color = Ink, fontSize = 36.sp, fontWeight = FontWeight.Bold)
                            LinearProgressIndicator(progress = { (total.toFloat() / monthlyBudget.coerceAtLeast(1)).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Green, trackColor = Color(0xFFE1ECE7))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text("${percent(total, monthlyBudget)}% del presupuesto", color = Green, fontSize = 13.sp)
                                Text("Disponible ${clp((monthlyBudget - total).coerceAtLeast(0))}", color = Color.Gray, fontSize = 13.sp)
                            }
                        }
                    }
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryMetric("Por revisar", pendingReconciliation.toString(), Color(0xFF9A6500), Modifier.weight(1f))
                        SummaryMetric("Conciliados", reconciledCount.toString(), Green, Modifier.weight(1f))
                        SummaryMetric("Compromisos", clp(plannedCommitmentTotal), Ink, Modifier.weight(1f))
                    }
                }
                item {
                    Button(onClick = { showManualDialog = true }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp)) {
                        Text("＋  Agregar gasto", fontSize = 16.sp)
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text("Últimos movimientos", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            val recentSummary = monthExpenses.sortedByDescending { it.receivedAt }.take(3)
                            if (recentSummary.isEmpty()) {
                                Text("Tus gastos reales aparecerán aquí.", color = Color.Gray, fontSize = 13.sp)
                            } else {
                                recentSummary.forEach { event -> SummaryMovementRow(event) }
                            }
                        }
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
                    OutlinedButton(onClick = { showOperations = !showOperations }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showOperations) "Ocultar herramientas" else "Mostrar herramientas")
                    }
                    if (showOperations) {
                    OutlinedButton(onClick = { exportLauncher.launch("brujula-respaldo-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.csv") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Exportar respaldo CSV completo")
                    }
                    OutlinedButton(onClick = { syncExportLauncher.launch("brujula-sync-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.json") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Exportar paquete de sincronización")
                    }
                    OutlinedButton(onClick = {
                        syncImportLauncher.launch(arrayOf("application/json", "text/json", "text/*"))
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Importar paquete de sincronización")
                    }
                    OutlinedButton(onClick = { showCloudAccountDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (cloudAccountLabel == "Sin cuenta cloud") "Configurar cuenta cloud" else "Cuenta cloud: $cloudAccountLabel")
                    }
                    Text("La cuenta cloud permite recuperar tus movimientos en otro teléfono; la contraseña la gestiona Firebase y nunca se guarda en Brújula.", color = Color.Gray, fontSize = 12.sp)
                    syncStatus?.let { Text(it, color = Color.Gray, fontSize = 12.sp) }
                    OutlinedButton(onClick = {
                        syncStatus = "Sincronizando…"
                        firebaseSync.sync(CapturedEventStore(activity).recent(10_000)) { result -> syncStatus = result }
                    }, modifier = Modifier.fillMaxWidth()) {
                        Text("Sincronizar con la nube")
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
                }
                item {
                    OutlinedButton(onClick = { showSettings = !showSettings }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (showSettings) "Ocultar configuración" else "Mostrar configuración")
                    }
                    if (showSettings) {
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
                if (selectedTab == 1) {
                    val visibleMovements = captured.filter { event ->
                        val matchesFilter = when (movementFilter) {
                            "Pendientes" -> event.status == "captured" || event.status == "requires_review"
                            "Confirmados" -> event.status == "confirmed" || event.status == "reconciled"
                            else -> true
                        }
                        val query = movementQuery.trim()
                        val matchesQuery = query.isBlank() ||
                            event.merchant.orEmpty().contains(query, ignoreCase = true) ||
                            event.rawText.contains(query, ignoreCase = true)
                        val matchesPayment = when (movementPayment) {
                            "Tarjeta" -> event.paymentSource == "tarjeta_credito"
                            "Cuenta" -> event.paymentSource == "cuenta_corriente"
                            else -> true
                        }
                        matchesFilter && matchesQuery && matchesPayment
                    }
                    item {
                        Text("Tus movimientos", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Busca por comercio o texto y filtra lo que requiere revisión.", color = Color.Gray, fontSize = 13.sp)
                        OutlinedTextField(
                            value = movementQuery,
                            onValueChange = { movementQuery = it },
                            label = { Text("Buscar movimiento") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Todos", "Pendientes", "Confirmados").forEach { filter ->
                                FilterChip(
                                    selected = movementFilter == filter,
                                    onClick = { movementFilter = filter },
                                    label = { Text(filter) }
                                )
                            }
                        }
                        Text("Medio de pago", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Todos", "Tarjeta", "Cuenta").forEach { payment ->
                                FilterChip(
                                    selected = movementPayment == payment,
                                    onClick = { movementPayment = payment },
                                    label = { Text(payment) }
                                )
                            }
                        }
                        Text("${visibleMovements.size} movimiento(s) visible(s)", color = Color.Gray, fontSize = 12.sp)
                    }
                    if (visibleMovements.isEmpty()) {
                        item { Text("No hay movimientos que coincidan con esta búsqueda.", color = Color.Gray, fontSize = 13.sp) }
                    } else {
                        items(visibleMovements) { event ->
                            CapturedEventRow(event) {
                                CapturedEventStore(activity).updateStatus(event.id, "confirmed")
                                captured = CapturedEventStore(activity).recent()
                            }
                        }
                    }
                }
                if (selectedTab == 2) {
                    item {
                        Text("Captura de gastos", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("Scotiabank se captura desde sus notificaciones. También puedes registrar un gasto manual.", color = Color.Gray, fontSize = 13.sp)
                        OutlinedButton(onClick = { showManualDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Agregar gasto manual") }
                        OutlinedButton(onClick = { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) }, modifier = Modifier.fillMaxWidth()) { Text("Configurar notificaciones") }
                    }
                }
                if (selectedTab == 3) {
                    item {
                        Text("Planificación mensual${if (planningVersion >= 0) "" else ""}", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text("La vista que faltaba para llevar tu método de Excel: ingresos, compromisos y cuotas.", color = Color.Gray, fontSize = 13.sp)
                        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Resumen planificado", color = Ink, fontWeight = FontWeight.Bold)
                                Text("Ingresos registrados: ${clp(plannedIncomeTotal)}", color = Green)
                                Text("Compromisos activos: ${clp(plannedCommitmentTotal)}", color = Color(0xFF9A6500))
                                Text("Disponible estimado: ${clp(plannedIncomeTotal - plannedCommitmentTotal)}", color = Ink, fontWeight = FontWeight.SemiBold)
                                Text(if (monthlyGoal > 0) "Meta mensual: ${clp(monthlyGoal)}" else "Meta mensual: aún no configurada", color = Color.Gray, fontSize = 12.sp)
                            }
                        }
                        Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Proyección de compromisos", color = Ink, fontWeight = FontWeight.Bold)
                                Text("Incluye cuotas futuras y gastos recurrentes registrados.", color = Color.Gray, fontSize = 12.sp)
                                projections.forEach { projection ->
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text(projection.label.replaceFirstChar { it.uppercase() }, color = Color.Gray, fontSize = 12.sp)
                                        Text(clp(projection.commitmentTotal), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        OutlinedButton(onClick = { showIncomeDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Agregar ingreso") }
                        OutlinedButton(onClick = { showCommitmentDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Agregar compromiso o cuota") }
                        OutlinedButton(onClick = { showGoalDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Configurar meta mensual") }
                        if (planningStore.incomes().isNotEmpty()) {
                            Text("Ingresos", color = Ink, fontWeight = FontWeight.Bold)
                            planningStore.incomes().forEach { income ->
                                PlanningRow("${income.name} · ${clp(income.amount)}", "Ingreso recurrente") {
                                    planningStore.deleteIncome(income.id); planningVersion++
                                }
                            }
                        }
                        if (activeCommitments.isNotEmpty()) {
                            Text("Compromisos activos", color = Ink, fontWeight = FontWeight.Bold)
                            activeCommitments.forEach { commitment ->
                                val installment = if (commitment.hasInstallments) "Cuota ${commitment.currentInstallment}/${commitment.totalInstallments}" else "Recurrente"
                                PlanningRow("${commitment.name} · ${clp(commitment.amount)}", installment) {
                                    planningStore.deleteCommitment(commitment.id); planningVersion++
                                }
                            }
                        }
                    }
                }
                if (selectedTab == 4) {
                    item {
                        Text("Cuenta y respaldo", color = Ink, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(if (cloudAccountLabel == "Sin cuenta cloud") "Conecta una cuenta cloud para recuperar tus movimientos." else "Cuenta activa: $cloudAccountLabel", color = Color.Gray, fontSize = 13.sp)
                        OutlinedButton(onClick = { showCloudAccountDialog = true }, modifier = Modifier.fillMaxWidth()) { Text("Configurar cuenta cloud") }
                        if (cloudAccountLabel != "Sin cuenta cloud") {
                            Button(
                                onClick = {
                                    syncStatus = "Sincronizando…"
                                    firebaseSync.sync(CapturedEventStore(activity).recent(10_000)) { result -> syncStatus = result }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Sincronizar y recuperar movimientos") }
                        }
                        OutlinedButton(
                            onClick = { syncExportLauncher.launch("brujula-sync-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.json") },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Exportar respaldo portátil") }
                        OutlinedButton(
                            onClick = { syncImportLauncher.launch(arrayOf("application/json", "text/json", "text/*")) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Importar respaldo portátil") }
                        syncStatus?.let { Text(it, color = Color.Gray, fontSize = 12.sp) }
                        OutlinedButton(onClick = { showOperations = true; selectedTab = 0 }, modifier = Modifier.fillMaxWidth()) { Text("Abrir herramientas avanzadas") }
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
                            listOf("servicios" to "Servicios", "casa" to "Casa", "supermercado" to "Supermercado", "salidas" to "Salidas", "auto" to "Auto", "otros" to "Otros").forEach { (value, label) ->
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
        if (showCloudAccountDialog) {
            var accountEmail by remember { mutableStateOf("") }
            var accountPassword by remember { mutableStateOf("") }
            var accountMessage by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { showCloudAccountDialog = false },
                title = { Text("Cuenta cloud") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Usa un correo y contraseña sólo para la cuenta cloud de Brújula. No son tus credenciales bancarias.", color = Color.Gray, fontSize = 12.sp)
                        OutlinedTextField(value = accountEmail, onValueChange = { accountEmail = it }, label = { Text("Correo") }, singleLine = true)
                        OutlinedTextField(value = accountPassword, onValueChange = { accountPassword = it }, label = { Text("Contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                        accountMessage?.let { Text(it, color = Color.Gray, fontSize = 12.sp) }
                        if (cloudAccountLabel != "Sin cuenta cloud") {
                            Text("Sesión activa: $cloudAccountLabel", color = Green, fontSize = 12.sp)
                            TextButton(onClick = {
                                firebaseSync.signOut()
                                cloudAccountLabel = "Sin cuenta cloud"
                                syncStatus = "Sesión cloud cerrada."
                                showCloudAccountDialog = false
                            }) { Text("Cerrar sesión") }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        if (accountEmail.isBlank() || accountPassword.length < 6) {
                            accountMessage = "Ingresa un correo y una contraseña de al menos 6 caracteres."
                        } else {
                            firebaseSync.signIn(accountEmail, accountPassword) { result ->
                                accountMessage = result
                                firebaseSync.currentUserEmail()?.let { cloudAccountLabel = it }
                            }
                        }
                    }) { Text("Iniciar sesión") }
                },
                dismissButton = {
                    Row {
                        TextButton(onClick = {
                            if (accountEmail.isBlank() || accountPassword.length < 6) {
                                accountMessage = "Ingresa un correo y una contraseña de al menos 6 caracteres."
                            } else {
                                firebaseSync.createAccount(accountEmail, accountPassword) { result ->
                                    accountMessage = result
                                    firebaseSync.currentUserEmail()?.let { cloudAccountLabel = it }
                                }
                            }
                        }) { Text("Crear cuenta") }
                        TextButton(onClick = { showCloudAccountDialog = false }) { Text("Cancelar") }
                    }
                }
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
        if (showGoalDialog) {
            var goalInput by remember { mutableStateOf(monthlyGoal.toString()) }
            var goalError by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { showGoalDialog = false },
                title = { Text("Meta mensual") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = goalInput, onValueChange = { goalInput = it }, label = { Text("Monto objetivo en pesos") }, singleLine = true)
                        Text("Puede ser una meta de ahorro, ingreso o saldo disponible. Brújula no la interpretará por ti.", color = Color.Gray, fontSize = 12.sp)
                        goalError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val value = parseClpInput(goalInput)
                        if (value == null || value < 0) goalError = "Ingresa un monto válido."
                        else { planningStore.setMonthlyGoal(value); planningVersion++; showGoalDialog = false }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { showGoalDialog = false }) { Text("Cancelar") } }
            )
        }
        if (showIncomeDialog) {
            var name by remember { mutableStateOf("") }
            var amountInput by remember { mutableStateOf("") }
            var error by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { showIncomeDialog = false },
                title = { Text("Agregar ingreso") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre: sueldo, DAP, Integra…") }, singleLine = true)
                        OutlinedTextField(value = amountInput, onValueChange = { amountInput = it }, label = { Text("Monto mensual") }, singleLine = true)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val amount = parseClpInput(amountInput)
                        if (name.isBlank() || amount == null || amount <= 0) error = "Completa nombre y monto."
                        else { planningStore.addIncome(name, amount); planningVersion++; showIncomeDialog = false }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { showIncomeDialog = false }) { Text("Cancelar") } }
            )
        }
        if (showCommitmentDialog) {
            var name by remember { mutableStateOf("") }
            var amountInput by remember { mutableStateOf("") }
            var currentInput by remember { mutableStateOf("") }
            var totalInput by remember { mutableStateOf("") }
            var category by remember { mutableStateOf("servicios") }
            var error by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { showCommitmentDialog = false },
                title = { Text("Compromiso o cuota") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ej.: PC, diplomado, mamá") }, singleLine = true)
                        OutlinedTextField(value = amountInput, onValueChange = { amountInput = it }, label = { Text("Monto de la cuota") }, singleLine = true)
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = currentInput, onValueChange = { currentInput = it }, label = { Text("Cuota actual") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = totalInput, onValueChange = { totalInput = it }, label = { Text("Total cuotas") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                        Text("Categoría", fontWeight = FontWeight.SemiBold)
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("servicios", "casa", "supermercado", "salidas", "auto").forEach { value ->
                                FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value.replaceFirstChar { it.uppercase() }) })
                            }
                        }
                        Text("Deja las cuotas vacías si es un gasto recurrente sin término.", color = Color.Gray, fontSize = 12.sp)
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val amount = parseClpInput(amountInput)
                        val current = currentInput.toIntOrNull() ?: 0
                        val total = totalInput.toIntOrNull() ?: 0
                        if (name.isBlank() || amount == null || amount <= 0 || current < 0 || total < 0 || (total > 0 && current > total)) error = "Revisa nombre, monto y cuotas."
                        else { planningStore.addCommitment(name, amount, current, total, category); planningVersion++; showCommitmentDialog = false }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { showCommitmentDialog = false }) { Text("Cancelar") } }
            )
        }
        editingEvent?.let { event ->
            AlertDialog(
                onDismissRequest = { editingEvent = null },
                title = { Text("Categoría del movimiento") },
                text = {
                    Column {
                        listOf("servicios", "casa", "supermercado", "salidas", "auto", "otros").forEach { category ->
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
}

@Composable private fun PlanningRow(title: String, subtitle: String, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            TextButton(onClick = onDelete) { Text("Quitar") }
        }
    }
}

@Composable private fun SummaryMetric(label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, color = Color.Gray, fontSize = 11.sp)
            Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable private fun SummaryMovementRow(event: CapturedEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4F0))) {
            Text("•", color = Green, fontSize = 22.sp, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(event.merchant ?: "Movimiento", color = Ink, fontWeight = FontWeight.SemiBold)
            Text("${event.categoryLabel()} · ${event.receivedAtShortLabel()}", color = Color.Gray, fontSize = 12.sp)
        }
        Text(clp(event.amount ?: 0), color = Ink, fontWeight = FontWeight.Bold)
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


private fun syncBundleJson(events: List<CapturedEvent>, planning: JSONObject? = null): String = JSONObject().apply {
    put("schema_version", 1)
    put("generated_at", System.currentTimeMillis())
    put("events", JSONArray().apply {
        events.forEach { event ->
            put(JSONObject().apply {
                put("fingerprint", event.fingerprint)
                put("received_at", event.receivedAt)
                put("source_package", event.sourcePackage)
                put("raw_text", event.rawText)
                put("amount", event.amount ?: JSONObject.NULL)
                put("merchant", event.merchant ?: JSONObject.NULL)
                put("movement_type", event.movementType)
                put("category", event.category)
                put("payment_source", event.paymentSource)
                put("status", event.status)
            })
        }
    })
    planning?.let { put("planning", it) }
}.toString(2)

private fun importSyncBundle(activity: MainActivity, json: String): Int {
    val root = runCatching { JSONObject(json) }.getOrNull() ?: return 0
    if (root.optInt("schema_version", -1) != 1) return 0
    val events = root.optJSONArray("events") ?: return 0
    val store = CapturedEventStore(activity)
    val importedEvents = (0 until events.length()).count { index ->
        val event = events.optJSONObject(index) ?: return@count false
        val fingerprint = event.optString("fingerprint").takeIf { it.isNotBlank() } ?: return@count false
        val status = event.optString("status", "captured")
        if (status == "demo" || status == "ignored") return@count false
        store.insertSyncEvent(
            fingerprint = fingerprint,
            receivedAt = event.optLong("received_at", System.currentTimeMillis()),
            sourcePackage = event.optString("source_package", "cl.brujula.app.sync"),
            rawText = event.optString("raw_text"),
            amount = if (event.isNull("amount")) null else event.optInt("amount"),
            merchant = if (event.isNull("merchant")) null else event.optString("merchant"),
            movementType = event.optString("movement_type", "expense"),
            category = event.optString("category", "otros"),
            paymentSource = event.optString("payment_source", "notificacion"),
            status = status
        )
    }
    PlanningStore(activity).importJson(root.optJSONObject("planning") ?: JSONObject())
    return importedEvents
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
    "servicios" -> "SERVICIOS"
    "casa" -> "CASA"
    "supermercado" -> "SUPERMERCADO"
    "salidas" -> "SALIDAS"
    "auto" -> "AUTO"
    "alimentacion" -> "SUPERMERCADO"
    "transporte" -> "SALIDAS"
    "suscripciones" -> "SERVICIOS"
    "salud" -> "CASA"
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
            status = when (statusLabel) {
                "CONFIRMADO" -> "confirmed"
                "CONCILIADO" -> "reconciled"
                "REQUIERE_REVISION" -> "requires_review"
                else -> "captured"
            }
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
