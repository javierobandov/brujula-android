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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.semantics.Role
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

private val Green = Color(0xFF173F38)
private val Paper = Color(0xFFF7F3E9)
private val Ink = Color(0xFF172522)
private val Mint = Color(0xFFDCEBE3)
private val Lavender = Color(0xFFF4D9CF)
private val Muted = Color(0xFF66736F)
private val Coral = Color(0xFFE76548)
private val Ochre = Color(0xFFF0C85B)
private val Line = Color(0xFFD6DFD9)
private val BudgetCategories = listOf("alimentacion", "vivienda", "transporte", "salud", "servicios", "ocio", "compras", "deudas", "otros")
private val CategoryColors = listOf(Green, Coral, Ochre, Color(0xFF6C8E84), Color(0xFF8C6A57), Color(0xFFB86A6A), Color(0xFF7A9A72), Color(0xFF8B7767), Color(0xFF9CA9A4))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) { super.onCreate(savedInstanceState); setContent { BrujulaApp(this) } }
}

@Composable fun BrujulaApp(activity: MainActivity) {
    var syncing by remember { mutableStateOf(false) }
    var checkingUpdate by remember { mutableStateOf(false) }
    var updateStatus by remember { mutableStateOf<String?>(null) }
    var availableUpdate by remember { mutableStateOf<AppUpdate?>(null) }
    val updateChecker = remember { UpdateChecker(activity) }
    val firebaseSync = remember { FirebaseCloudSync(activity) }
    var cloudAccountLabel by remember { mutableStateOf(firebaseSync.currentUserEmail() ?: "Sin cuenta cloud") }
    var showCloudAccountDialog by remember { mutableStateOf(false) }
    var captured by remember { mutableStateOf(CapturedEventStore(activity).recent(10_000)) }
    var editingEvent by remember { mutableStateOf<CapturedEvent?>(null) }
    var showManualDialog by remember { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var statementStatus by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showIncomeDialog by remember { mutableStateOf(false) }
    var showCommitmentDialog by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    var showCategoryLimitDialog by remember { mutableStateOf(false) }
    var planningVersion by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableStateOf(0) }
    var movementFilter by remember { mutableStateOf("Todos") }
    var movementQuery by remember { mutableStateOf("") }
    var movementPayment by remember { mutableStateOf("Todos") }
    var calendarMonth by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var calendarYear by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedCalendarDay by remember { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }
    val statementLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val csv = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val store = CapturedEventStore(activity)
        val imported = importStatementCsv(activity, csv)
        val reconciled = store.reconcileStatements()
        captured = store.recent(10_000)
        statementStatus = if (imported == 0) "No se reconocieron movimientos de cartola." else "Cartola: $imported movimientos importados; $reconciled conciliados automáticamente."
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val csv = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val imported = importCapturedCsv(activity, csv)
        captured = CapturedEventStore(activity).recent(10_000)
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
        captured = CapturedEventStore(activity).recent(10_000)
        syncStatus = if (imported == 0) "No se encontraron movimientos nuevos en el paquete." else "Se incorporaron $imported movimientos nuevos."
    }
    LaunchedEffect(Unit) {
        while (true) {
            delay(15_000L)
            captured = CapturedEventStore(activity).recent(10_000)
        }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                captured = CapturedEventStore(activity).recent(10_000)
                updateChecker.resumePendingInstall { updateStatus = it }
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
    val previousMonthExpenses = expenses.filter { it.receivedAt.isPreviousMonth() }
    val monthRefunds = realCaptured.filter { it.movementType == "refund" && it.receivedAt.isCurrentMonth() }
    val total = monthExpenses.sumOf { it.amount ?: 0 }
    val previousTotal = previousMonthExpenses.sumOf { it.amount ?: 0 }
    val refunds = monthRefunds.sumOf { it.amount ?: 0 }
    val netTotal = (total - refunds).coerceAtLeast(0)
    val calendarExpenses = expenses.filter { it.receivedAt.isMonth(calendarYear, calendarMonth) }
    val planningStore = remember { PlanningStore(activity) }
    val categoryTotals = BudgetCategories
        .map { category -> category to monthExpenses.filter { it.category.normalizedBudgetCategory() == category }.sumOf { it.amount ?: 0 } }
    val paymentTotals = listOf("tarjeta_credito", "cuenta_corriente", "manual", "cartola")
        .map { source -> source to monthExpenses.filter { it.paymentSource == source }.sumOf { it.amount ?: 0 } }
    val categoryLimits = planningStore.categoryLimits().associate { it.category.normalizedBudgetCategory() to it.amount }
    val uncategorizedTotal = monthExpenses.filter { it.category.normalizedBudgetCategory() == "otros" }.sumOf { it.amount ?: 0 }
    val plannedIncomeTotal = planningStore.incomes().sumOf { it.amount }
    val monthlyBudget = plannedIncomeTotal
    val activeCommitments = planningStore.commitments().filter { it.isActive }
    val plannedCommitmentTotal = activeCommitments.sumOf { it.amount }
    val monthlyGoal = planningStore.monthlyGoal()
    val plannedAvailable = plannedIncomeTotal - plannedCommitmentTotal - monthlyGoal
    val realAvailable = plannedAvailable - netTotal
    val daysElapsed = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)
    val daysInMonth = Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)
    val dailyAverage = if (daysElapsed > 0) netTotal / daysElapsed else 0
    val dailySafeAmount = if (daysElapsed < daysInMonth) (realAvailable.coerceAtLeast(0) / (daysInMonth - daysElapsed).coerceAtLeast(1)) else 0
    val projections = planningStore.projections()
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            secondary = Coral,
            onSecondary = Ink,
            tertiary = Ochre,
            background = Paper,
            surface = Color.White,
            onSurface = Ink,
            onBackground = Ink
        ),
        typography = Typography(
            displayLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, letterSpacing = (-1.4).sp),
            headlineLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal, letterSpacing = (-1.1).sp),
            titleLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal),
            bodyLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif),
            bodyMedium = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif),
            labelLarge = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.SansSerif, fontWeight = FontWeight.Bold)
        )
    ) {
        Surface(color = Paper, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize()) {
            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                if (selectedTab == 0) {
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Image(painter = painterResource(R.drawable.brujula_logo), contentDescription = "Logo de Brújula", modifier = Modifier.size(38.dp))
                            Text("BRÚJULA", color = Green, fontSize = 22.sp, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
                        }
                        Card(shape = RoundedCornerShape(50), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4F0)), modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp).clickable(role = Role.Button) { selectedTab = 4 }) {
                            Icon(Icons.Filled.Person, contentDescription = "Cuenta", tint = Green, modifier = Modifier.padding(10.dp).size(26.dp))
                        }
                    }
                }
                item {
                    SummaryHeroCard(realAvailable, netTotal, plannedIncomeTotal, plannedCommitmentTotal, monthlyGoal)
                }
                item {
                    BehaviorChartCard(calendarExpenses, netTotal, dailyAverage)
                }
                item {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SummaryMetric(Icons.Filled.AccountBalanceWallet, "Presupuesto", clp(plannedAvailable.coerceAtLeast(0)), Green, Modifier.weight(1f))
                        SummaryMetric(Icons.Filled.Check, "Conciliación", reconciledCount.toString(), Green, Modifier.weight(1f))
                        SummaryMetric(Icons.Filled.Description, "Por revisar", pendingReconciliation.toString(), if (pendingReconciliation > 0) Color(0xFF9A6500) else Green, Modifier.weight(1f))
                    }
                }
                item {
                    Button(
                        onClick = { showManualDialog = true },
                        modifier = Modifier.fillMaxWidth().heightIn(min = 56.dp),
                        shape = RoundedCornerShape(18.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Green),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp, pressedElevation = 1.dp)
                    ) { Text("＋  Agregar gasto", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                }
                item {
                    CalendarSpendingCard(
                        expenses = calendarExpenses,
                        month = calendarMonth,
                        year = calendarYear,
                        selectedDay = selectedCalendarDay,
                        onMonthChange = { delta ->
                            val next = Calendar.getInstance().apply { set(calendarYear, calendarMonth, 1); add(Calendar.MONTH, delta) }
                            calendarMonth = next.get(Calendar.MONTH)
                            calendarYear = next.get(Calendar.YEAR)
                            selectedCalendarDay = 1
                        },
                        onDaySelected = { selectedCalendarDay = it },
                        onViewMovements = { movementQuery = ""; movementFilter = "Todos"; selectedTab = 1 }
                    )
                }
                item {
                    CategoryDistributionCard(categoryTotals, netTotal, categoryLimits, uncategorizedTotal)
                }
                item {
                    SpendingPaceCard(netTotal, previousTotal, dailyAverage, dailySafeAmount, realAvailable)
                }
                item {
                    BreakdownCard(
                        title = "Gasto por medio de pago",
                        rows = paymentTotals.map { (source, amount) -> source.replace("_", " ").replaceFirstChar { it.uppercase() } to amount },
                        emptyLabel = "Todavía no hay gastos con medio de pago identificado."
                    )
                }
                if (pendingReconciliation > 0 || uncategorizedTotal > 0) {
                    item {
                        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF4E5)), modifier = Modifier.clickable(role = Role.Button) { movementFilter = "Pendientes"; selectedTab = 1 }) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("Hay decisiones pendientes", color = Ink, fontWeight = FontWeight.Bold)
                                if (pendingReconciliation > 0) Text("$pendingReconciliation movimientos requieren revisión.", color = Muted, fontSize = 12.sp)
                                if (uncategorizedTotal > 0) Text("${clp(uncategorizedTotal)} están en Otros y pueden mejorar tu análisis.", color = Muted, fontSize = 12.sp)
                                TextButton(onClick = { movementFilter = "Pendientes"; selectedTab = 1 }) { Text("Abrir pendientes →", color = Green, fontWeight = FontWeight.Bold) }
                            }
                        }
                    }
                }
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Actividad reciente", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                                TextButton(onClick = { selectedTab = 1 }, modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)) { Text("Ver todo", fontSize = 12.sp, color = Green) }
                            }
                            val recentSummary = monthExpenses.sortedByDescending { it.receivedAt }.take(3)
                            if (recentSummary.isEmpty()) {
                                Text("Aún no hay gastos reales.", color = Muted, fontSize = 13.sp)
                            } else {
                                recentSummary.forEach { event -> SummaryMovementRow(event) }
                            }
                        }
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
                        Text("REGISTRO", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("Movimientos", color = Ink, fontSize = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
                        Text("Revisa, clasifica y confirma cada gasto.", color = Muted, fontSize = 14.sp)
                        OutlinedTextField(
                            value = movementQuery,
                            onValueChange = { movementQuery = it },
                            placeholder = { Text("Buscar comercio, categoría o monto…") },
                            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = Green) },
                            singleLine = true,
                            shape = RoundedCornerShape(50),
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = Color(0xFFE3E8E5), focusedBorderColor = Green, unfocusedContainerColor = Color.White, focusedContainerColor = Color.White)
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
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("${visibleMovements.size} movimiento(s) visible(s)", color = Muted, fontSize = 12.sp)
                            TextButton(onClick = { selectedTab = 0 }) { Text("Ver calendario", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    if (visibleMovements.isEmpty()) {
                        item { Text("No hay movimientos que coincidan con esta búsqueda.", color = Muted, fontSize = 13.sp) }
                    } else {
                        items(visibleMovements) { event ->
                            if (event.status == "requires_review" || event.status == "captured") {
                                CapturedExpenseRow(
                                    event = event,
                                    onEditCategory = { editingEvent = event },
                                    onConfirm = {
                                        CapturedEventStore(activity).updateStatus(event.id, "confirmed")
                                        captured = CapturedEventStore(activity).recent(10_000)
                                    }
                                )
                            } else {
                                CapturedEventRow(event)
                            }
                        }
                    }
                }
                if (selectedTab == 2) {
                    item {
                        Text("AUTOMÁTICO", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("Captura", color = Ink, fontSize = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
                        Text("Deja que Brújula registre tus gastos desde las notificaciones.", color = Muted, fontSize = 14.sp)
                        Card(shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Mint)) {
                            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("Captura de Scotiabank", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                Text("La app solo lee las notificaciones autorizadas en Android. No necesita tus claves bancarias.", color = Muted, fontSize = 13.sp)
                            }
                        }
                        Card(shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                ActionRow(Icons.Filled.Description, "Configurar notificaciones", "Activar acceso de captura de Android", { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
                            }
                        }
                    }
                }
                if (selectedTab == 3) {
                    item {
                        Text("PLAN DEL MES", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("Planificación", color = Ink, fontSize = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
                        Text("Decide cuánto puedes gastar antes de que ocurra.", color = Muted, fontSize = 14.sp)
                        Spacer(Modifier.height(6.dp))
                        PlanHeroCard(plannedIncomeTotal, plannedCommitmentTotal, monthlyGoal, plannedAvailable)
                        Text("Acciones rápidas", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PlanActionTile(Icons.Filled.AccountBalanceWallet, "Ingreso", { showIncomeDialog = true }, Modifier.weight(1f))
                            PlanActionTile(Icons.Filled.Description, "Compromiso", { showCommitmentDialog = true }, Modifier.weight(1f))
                            PlanActionTile(Icons.Filled.Check, "Meta", { showGoalDialog = true }, Modifier.weight(1f))
                        }
                        Spacer(Modifier.height(14.dp))
                        val limitRows = BudgetCategories.map { category ->
                            val spent = categoryTotals.firstOrNull { it.first == category }?.second ?: 0
                            category to (spent to (categoryLimits[category] ?: 0))
                        }.filter { it.second.first > 0 || it.second.second > 0 }
                        Card(shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Column(Modifier.weight(1f)) {
                                        Text("Control por área", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        Text("Gasto real frente a tus límites", color = Muted, fontSize = 12.sp)
                                    }
                                    TextButton(onClick = { showCategoryLimitDialog = true }) { Text("Editar", color = Green, fontWeight = FontWeight.Bold) }
                                }
                                if (limitRows.isEmpty()) {
                                    Text("Configura límites para saber dónde debes ajustar.", color = Muted, fontSize = 13.sp)
                                } else {
                                    limitRows.sortedByDescending { it.second.first }.take(3).forEach { (category, values) ->
                                        CategoryLimitRow(category, values.first, values.second)
                                    }
                                    if (limitRows.size > 3) Text("+ ${limitRows.size - 3} áreas configuradas", color = Muted, fontSize = 11.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Card(shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = Lavender)) {
                                        Icon(Icons.Filled.BarChart, contentDescription = null, tint = Green, modifier = Modifier.padding(9.dp).size(20.dp))
                                    }
                                    Column {
                                        Text("Calendario de compromisos", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                                        Text("Lo que ya está reservado en los próximos meses", color = Muted, fontSize = 12.sp)
                                    }
                                }
                                if (projections.none { it.commitmentTotal > 0 }) {
                                    Text("Todavía no hay cuotas ni gastos recurrentes. Agrégalos para proyectar tu flujo.", color = Muted, fontSize = 13.sp)
                                } else {
                                    projections.take(4).forEach { projection ->
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text(projection.label.replaceFirstChar { it.uppercase() }, color = Muted, fontSize = 12.sp)
                                            Text(clp(projection.commitmentTotal), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        if (planningStore.incomes().isNotEmpty()) {
                            Text("Ingresos registrados", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(2.dp))
                            planningStore.incomes().forEach { income ->
                                PlanningRow("${income.name} · ${clp(income.amount)}", "Ingreso recurrente") {
                                    planningStore.deleteIncome(income.id); planningVersion++
                                }
                            }
                        } else {
                            Text("Aún no has registrado ingresos. Agrégalos para calcular tu disponible.", color = Muted, fontSize = 12.sp)
                        }
                        if (activeCommitments.isNotEmpty()) {
                            Spacer(Modifier.height(12.dp))
                            Text("Compromisos activos", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            activeCommitments.forEach { commitment ->
                                val installment = if (commitment.hasInstallments) "Cuota ${commitment.currentInstallment}/${commitment.totalInstallments}" else "Recurrente"
                                PlanningRow("${commitment.name} · ${clp(commitment.amount)}", installment) {
                                    planningStore.deleteCommitment(commitment.id); planningVersion++
                                }
                            }
                        } else {
                            Text("Sin compromisos activos. Tu proyección se mantiene en cero.", color = Muted, fontSize = 12.sp)
                        }
                    }
                }
                if (selectedTab == 4) {
                    item {
                        Text("TU ESPACIO", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                        Text("Cuenta", color = Ink, fontSize = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
                        Text(if (cloudAccountLabel == "Sin cuenta cloud") "Tus datos viven en este teléfono. La nube es opcional." else "Cuenta activa: $cloudAccountLabel", color = Muted, fontSize = 14.sp)
                        Card(shape = RoundedCornerShape(22.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Lavender)) {
                            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Card(shape = RoundedCornerShape(50), colors = CardDefaults.cardColors(containerColor = Color(0xFFE1D2FF))) {
                                    Icon(Icons.Filled.Person, contentDescription = null, tint = Green, modifier = Modifier.padding(11.dp).size(22.dp))
                                }
                                Column {
                                    Text("Tus datos son tuyos", color = Ink, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                    Text("Brújula funciona sin enviar información a terceros.", color = Muted, fontSize = 12.sp)
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        Text("Sincronización", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Card(shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                ActionRow(Icons.Filled.Person, "Cuenta cloud", "Recuperar movimientos en otro teléfono", { showCloudAccountDialog = true })
                                if (cloudAccountLabel != "Sin cuenta cloud") {
                                    ActionRow(Icons.Filled.Check, if (syncing) "Sincronizando…" else "Sincronizar ahora", "Enviar y recuperar movimientos", {
                                        if (!syncing) {
                                            syncing = true
                                            syncStatus = "Sincronizando…"
                                            firebaseSync.sync(CapturedEventStore(activity).recent(10_000)) { result ->
                                                syncStatus = result
                                                syncing = false
                                            }
                                        }
                                    }, emphasized = true, enabled = !syncing)
                                }
                                ActionRow(Icons.Filled.Description, "Respaldo portátil", "Exportar o importar un archivo local", {
                                    syncExportLauncher.launch("brujula-sync-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.json")
                                })
                                ActionRow(Icons.Filled.List, "Importar respaldo", "Seleccionar un archivo existente", {
                                    syncImportLauncher.launch(arrayOf("application/json", "text/json", "text/*"))
                                })
                            }
                        }
                        syncStatus?.let { Text(it, color = Green, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                        Text("Preferencias", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Card(shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                ActionRow(Icons.Filled.Description, "Captura de notificaciones", "Permisos de Android para Scotiabank", { selectedTab = 2 })
                                ActionRow(Icons.Filled.BarChart, "Consultar con ChatGPT", "Compartir un resumen sin datos sensibles", {
                                    activity.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_SUBJECT, "Resumen financiero Brújula")
                                        putExtra(Intent.EXTRA_TEXT, chatGptBrief(monthlyBudget, total, monthExpenses, pendingReconciliation, reconciledCount))
                                    }, "Compartir resumen con ChatGPT"))
                                })
                                ActionRow(Icons.Filled.PlayArrow, "Herramientas avanzadas", "Pruebas y bandeja técnica", { selectedTab = 5 })
                                ActionRow(Icons.Filled.Check, if (checkingUpdate) "Buscando actualización…" else "Buscar actualización", updateStatus ?: "Comprobar e instalar una nueva versión", {
                                    if (!checkingUpdate) {
                                        checkingUpdate = true
                                        updateStatus = "Buscando actualización…"
                                        updateChecker.check { result ->
                                            checkingUpdate = false
                                            result.onSuccess { update ->
                                                availableUpdate = update
                                                updateStatus = if (update == null) "Brújula ya está actualizada." else "Disponible Brújula ${update.version}."
                                            }.onFailure { error -> updateStatus = "No se pudo comprobar: ${error.message ?: "error de red"}" }
                                        }
                                    }
                                }, enabled = !checkingUpdate)
                                availableUpdate?.let { update ->
                                    ActionRow(Icons.Filled.PlayArrow, "Instalar Brújula ${update.version}", "Descargar y abrir instalador de Android", {
                                        updateChecker.downloadAndInstall(update) { updateStatus = it }
                                    }, emphasized = true)
                                }
                            }
                        }
                        Text("Brújula no guarda credenciales bancarias, números de tarjeta ni el texto original al consultar ChatGPT.", color = Muted, fontSize = 11.sp, modifier = Modifier.padding(top = 4.dp))
                    }
                }
                if (selectedTab == 5) {
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("SOPORTE", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                                Text("Herramientas", color = Ink, fontSize = 30.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
                                Text("Pruebas locales y revisión técnica", color = Muted, fontSize = 14.sp)
                            }
                            TextButton(onClick = { selectedTab = 4 }) { Text("Volver") }
                        }
                    }
                    item {
                        Card(shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                ActionRow(Icons.Filled.Description, "Captura de notificaciones", "Abrir permisos de Android", { activity.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) })
                                ActionRow(Icons.Filled.PlayArrow, "Probar clasificación", "Insertar datos ficticios locales", {
                                    CapturedEventStore(activity).insertClassificationDemoEvents()
                                    captured = CapturedEventStore(activity).recent(10_000)
                                })
                            }
                        }
                    }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Bandeja capturada", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold)
                            TextButton(onClick = { captured = CapturedEventStore(activity).recent(10_000) }) { Text("Actualizar") }
                        }
                    }
                    if (captured.isEmpty()) {
                        item { Text("Todavía no hay eventos. Puedes insertar una prueba local o esperar una notificación real.", color = Muted, fontSize = 13.sp) }
                    } else {
                        items(captured) { event ->
                            CapturedEventRow(event) {
                                CapturedEventStore(activity).updateStatus(event.id, "confirmed")
                                captured = CapturedEventStore(activity).recent(10_000)
                            }
                        }
                    }
                    item { Text("Movimientos reales capturados", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                    if (realCaptured.isEmpty()) {
                        item { Text("Aún no hay movimientos reales. Los eventos DEMO no se incluyen en el resumen.", color = Muted, fontSize = 13.sp) }
                    } else {
                        items(realCaptured) { event ->
                            CapturedExpenseRow(
                                event = event,
                                onEditCategory = { editingEvent = event },
                                onConfirm = {
                                    CapturedEventStore(activity).updateStatus(event.id, "confirmed")
                                    captured = CapturedEventStore(activity).recent(10_000)
                                }
                            )
                        }
                    }
                }
            }
            NavigationBar(containerColor = Paper, tonalElevation = 0.dp) {
                listOf(0 to "Resumen", 1 to "Movimientos", 3 to "Planificación", 4 to "Cuenta").forEach { (tab, label) ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        colors = NavigationBarItemDefaults.colors(
                            indicatorColor = Mint,
                            selectedIconColor = Green,
                            selectedTextColor = Ink,
                            unselectedIconColor = Muted,
                            unselectedTextColor = Muted
                        ),
                        icon = {
                            Icon(
                                imageVector = when (tab) { 0 -> Icons.Filled.Home; 1 -> Icons.Filled.List; 3 -> Icons.Filled.BarChart; else -> Icons.Filled.Person },
                                contentDescription = label,
                                tint = if (selectedTab == tab) Green else Muted
                            )
                        },
                        label = { Text(label, fontSize = 11.sp) }
                    )
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
                                captured = CapturedEventStore(activity).recent(10_000)
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
                        Text("Usa un correo y contraseña sólo para la cuenta cloud de Brújula. No son tus credenciales bancarias.", color = Muted, fontSize = 12.sp)
                        OutlinedTextField(value = accountEmail, onValueChange = { accountEmail = it }, label = { Text("Correo") }, singleLine = true)
                        OutlinedTextField(value = accountPassword, onValueChange = { accountPassword = it }, label = { Text("Contraseña") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                        accountMessage?.let { Text(it, color = Muted, fontSize = 12.sp) }
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
        if (showGoalDialog) {
            var goalInput by remember { mutableStateOf(monthlyGoal.toString()) }
            var goalError by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { showGoalDialog = false },
                title = { Text("Meta mensual") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = goalInput, onValueChange = { goalInput = it }, label = { Text("Monto objetivo en pesos") }, singleLine = true)
                        Text("Puede ser una meta de ahorro, ingreso o saldo disponible. Brújula no la interpretará por ti.", color = Muted, fontSize = 12.sp)
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
        if (showCategoryLimitDialog) {
            var selectedCategory by remember { mutableStateOf(BudgetCategories.first()) }
            var limitInput by remember { mutableStateOf(planningStore.categoryLimit(selectedCategory).toString()) }
            var limitError by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { showCategoryLimitDialog = false },
                title = { Text("Límite por área") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Define cuánto quieres permitir en cada área durante el mes.", color = Muted, fontSize = 12.sp)
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            BudgetCategories.take(5).forEach { category ->
                                FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category; limitInput = planningStore.categoryLimit(category).toString() }, label = { Text(categoryLabel(category), fontSize = 11.sp) })
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                            BudgetCategories.drop(5).forEach { category ->
                                FilterChip(selected = selectedCategory == category, onClick = { selectedCategory = category; limitInput = planningStore.categoryLimit(category).toString() }, label = { Text(categoryLabel(category), fontSize = 11.sp) })
                            }
                        }
                        OutlinedTextField(value = limitInput, onValueChange = { limitInput = it }, label = { Text("Límite mensual en pesos") }, singleLine = true)
                        Text("Usa 0 para quitar el límite de esta área.", color = Muted, fontSize = 12.sp)
                        limitError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val value = parseClpInput(limitInput)
                        if (value == null || value < 0) limitError = "Ingresa un monto válido."
                        else { planningStore.setCategoryLimit(selectedCategory, value); planningVersion++; showCategoryLimitDialog = false }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { showCategoryLimitDialog = false }) { Text("Cancelar") } }
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
                            listOf("alimentacion", "vivienda", "transporte", "salud", "servicios", "ocio", "compras", "otros").forEach { value ->
                                FilterChip(selected = category == value, onClick = { category = value }, label = { Text(value.replaceFirstChar { it.uppercase() }) })
                            }
                        }
                        Text("Deja las cuotas vacías si es un gasto recurrente sin término.", color = Muted, fontSize = 12.sp)
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
                                listOf("alimentacion", "vivienda", "transporte", "salud", "servicios", "ocio", "compras", "otros").forEach { category ->
                            TextButton(onClick = {
                                CapturedEventStore(activity).updateCategory(event.id, category)
                                captured = CapturedEventStore(activity).recent(10_000)
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
    Card(shape = RoundedCornerShape(13.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            TextButton(onClick = onDelete) { Text("Quitar") }
        }
    }
}

@Composable private fun PlanHeroCard(income: Int, commitments: Int, goal: Int, available: Int) {
    val availableColor = if (available >= 0) Color.White else Coral
    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), colors = CardDefaults.cardColors(containerColor = Green)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("Disponible para gastar", color = Color(0xFFC7DCD4), fontSize = 13.sp)
                    Text(clp(available), color = availableColor, fontSize = 34.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
                }
                Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = if (available >= 0) Color(0xFF2C5D52) else Coral)) {
                    Text(if (available >= 0) "En control" else "Revisar plan", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp))
                }
            }
            HorizontalDivider(color = Color(0x557CA69A))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                PlanMetric("Ingresos", income)
                PlanMetric("Compromisos", commitments)
                PlanMetric("Meta", goal)
            }
        }
    }
}

@Composable private fun PlanMetric(label: String, amount: Int) {
    Column(Modifier.widthIn(min = 74.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Color(0xFFC7DCD4), fontSize = 10.sp)
        Text(clp(amount), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable private fun PlanActionTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.heightIn(min = 104.dp).clickable(role = Role.Button, onClick = onClick), shape = RoundedCornerShape(10.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Card(shape = RoundedCornerShape(8.dp), colors = CardDefaults.cardColors(containerColor = when (label) { "Compromiso" -> Color(0xFFF7E4B5); "Meta" -> Lavender; else -> Mint })) {
                Icon(icon, contentDescription = label, tint = Green, modifier = Modifier.padding(9.dp).size(21.dp))
            }
            Text(label, color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable private fun CalendarSpendingCard(
    expenses: List<CapturedEvent>,
    month: Int,
    year: Int,
    selectedDay: Int,
    onMonthChange: (Int) -> Unit,
    onDaySelected: (Int) -> Unit,
    onViewMovements: () -> Unit
) {
    val firstDay = Calendar.getInstance().apply { set(year, month, 1, 0, 0, 0) }
    val daysInMonth = firstDay.getActualMaximum(Calendar.DAY_OF_MONTH)
    val leading = (firstDay.get(Calendar.DAY_OF_WEEK) + 5) % 7
    val cellCount = ((leading + daysInMonth + 6) / 7) * 7
    val totalsByDay = expenses.groupBy { Calendar.getInstance().apply { timeInMillis = it.receivedAt }.get(Calendar.DAY_OF_MONTH) }
        .mapValues { (_, values) -> values.sumOf { it.amount ?: 0 } }
    val maxDayTotal = totalsByDay.values.maxOrNull() ?: 0
    val selectedEvents = expenses.filter { Calendar.getInstance().apply { timeInMillis = it.receivedAt }.get(Calendar.DAY_OF_MONTH) == selectedDay }
    val selectedTotal = selectedEvents.sumOf { it.amount ?: 0 }
    val average = if (daysInMonth > 0) expenses.sumOf { it.amount ?: 0 } / daysInMonth else 0
    val mostExpensive = totalsByDay.maxByOrNull { it.value }
    val monthLabel = SimpleDateFormat("MMMM yyyy", Locale("es", "CL")).format(firstDay.time).replaceFirstChar { it.uppercase() }
    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column {
                    Text("Gasto por día", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Detecta cuándo se concentra tu gasto", color = Muted, fontSize = 12.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { onMonthChange(-1) }, modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp)) { Text("‹", fontSize = 24.sp, color = Green) }
                    Text(monthLabel, color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onMonthChange(1) }, modifier = Modifier.sizeIn(minWidth = 40.dp, minHeight = 40.dp)) { Text("›", fontSize = 24.sp, color = Green) }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CalendarInsight("Promedio diario", clp(average), Icons.Filled.BarChart, Modifier.weight(1f))
                CalendarInsight("Día más caro", mostExpensive?.let { "${it.key} oct · ${clp(it.value)}" } ?: "Sin gastos", Icons.Filled.Warning, Modifier.weight(1f))
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                listOf("Lu", "Ma", "Mi", "Ju", "Vi", "Sá", "Do").forEach { day -> Text(day, color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f)) }
            }
            repeat(cellCount / 7) { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    repeat(7) { column ->
                        val day = row * 7 + column - leading + 1
                        if (day in 1..daysInMonth) {
                            val amount = totalsByDay[day] ?: 0
                            val fill = when {
                                amount == 0 -> Color(0xFFF1F4F2)
                                maxDayTotal > 0 && amount >= maxDayTotal * 0.66f -> Color(0xFFFFDAD6)
                                maxDayTotal > 0 && amount >= maxDayTotal * 0.33f -> Color(0xFFFFE9BF)
                                else -> Color(0xFFDDF1E9)
                            }
                            Box(Modifier.weight(1f).height(38.dp).padding(3.dp).background(if (day == selectedDay) Green else fill, RoundedCornerShape(50)).clickable(role = Role.Button) { onDaySelected(day) }, contentAlignment = Alignment.Center) {
                                Text(day.toString(), color = if (day == selectedDay) Color.White else Ink, fontSize = 12.sp, fontWeight = if (amount > 0 || day == selectedDay) FontWeight.Bold else FontWeight.Normal)
                            }
                        } else {
                            Spacer(Modifier.weight(1f).height(38.dp))
                        }
                    }
                }
            }
            Card(shape = RoundedCornerShape(15.dp), colors = CardDefaults.cardColors(containerColor = if (selectedTotal > 0) Color(0xFFFFF0EF) else Mint)) {
                Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text("${selectedDay} de ${monthLabel.substringBeforeLast(" ")}", color = Ink, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text(if (selectedTotal > 0) "${clp(selectedTotal)} · ${selectedEvents.size} movimiento(s)" else "Sin movimientos registrados", color = Muted, fontSize = 12.sp)
                    }
                    if (selectedTotal > 0) TextButton(onClick = onViewMovements) { Text("Ver movimientos", color = Green, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}

@Composable private fun CalendarInsight(label: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Lavender)) {
        Row(Modifier.padding(9.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            Icon(icon, contentDescription = null, tint = Green, modifier = Modifier.size(18.dp))
            Column {
                Text(label, color = Muted, fontSize = 10.sp, maxLines = 1)
                Text(value, color = Ink, fontSize = 11.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
        }
    }
}

@Composable private fun BehaviorChartCard(expenses: List<CapturedEvent>, total: Int, dailyAverage: Int) {
    val byDay = expenses.groupBy {
        Calendar.getInstance().apply { timeInMillis = it.receivedAt }.get(Calendar.DAY_OF_MONTH)
    }.mapValues { (_, events) -> events.sumOf { it.amount ?: 0 } }
    val days = (1..Calendar.getInstance().getActualMaximum(Calendar.DAY_OF_MONTH)).toList()
    val max = (byDay.values.maxOrNull() ?: 0).coerceAtLeast(1)
    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF172F35))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    Text("Ritmo de gasto", color = Color.White, fontSize = 17.sp, fontFamily = FontFamily.Serif)
                    Text("Acumulado del mes frente a tu ritmo esperado", color = Color(0xFFB8CCC6), fontSize = 11.sp)
                }
                Text(clp(total), color = Ochre, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Canvas(Modifier.fillMaxWidth().height(112.dp).padding(top = 8.dp)) {
                val step = size.width / (days.size - 1).coerceAtLeast(1)
                val points = days.mapIndexed { index, day ->
                    val amount = byDay[day] ?: 0
                    androidx.compose.ui.geometry.Offset(index * step, size.height - (amount.toFloat() / max * size.height))
                }
                for (index in 1 until points.size) {
                    drawLine(Color(0xFF67BEA9), points[index - 1], points[index], strokeWidth = 4f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                }
                points.filterIndexed { index, _ -> (byDay[days[index]] ?: 0) > 0 }.forEach { point ->
                    drawCircle(Ochre, radius = 4.5f, center = point)
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("1", color = Color(0xFFB8CCC6), fontSize = 9.sp)
                Text("Promedio diario ${clp(dailyAverage)}", color = Color(0xFFB8CCC6), fontSize = 9.sp)
                Text(days.last().toString(), color = Color(0xFFB8CCC6), fontSize = 9.sp)
            }
        }
    }
}

@Composable private fun SummaryHeroCard(available: Int, spent: Int, income: Int, commitments: Int, goal: Int) {
    val progress = (spent.toFloat() / (available + spent).coerceAtLeast(1)).coerceIn(0f, 1f)
    Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 0.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text("Disponible para gastar", color = Muted, fontSize = 13.sp)
                    Text(clp(available.coerceAtLeast(0)), color = Ink, fontSize = 35.sp, fontFamily = FontFamily.Serif, fontWeight = FontWeight.Normal)
                }
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(72.dp)) {
                    CircularProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxSize(),
                        color = Green,
                        trackColor = Line,
                        strokeWidth = 8.dp
                    )
                    Text("${(progress * 100).toInt()}%", color = Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                }
            }
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(6.dp), color = Green, trackColor = Line)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    Text("Gasto del mes", color = Muted, fontSize = 12.sp)
                    Text(clp(spent), color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                }
                Text("${(progress * 100).toInt()}% del presupuesto", color = Muted, fontSize = 11.sp)
            }
        }
    }
}

@Composable private fun SummaryHeroMetric(label: String, amount: Int) {
    Column(Modifier.widthIn(min = 76.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(label, color = Color(0xFFC7E7DE), fontSize = 10.sp)
        Text(clp(amount), color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, maxLines = 1)
    }
}

@Composable private fun CategoryDistributionCard(
    rows: List<Pair<String, Int>>,
    total: Int,
    limits: Map<String, Int>,
    uncategorized: Int
) {
    val visible = rows.filter { it.second > 0 }.sortedByDescending { it.second }
    Card(shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("Dónde se va el dinero", color = Ink, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Text(if (total > 0) "Distribución real de tus gastos del mes" else "Todavía no hay gastos para distribuir", color = Muted, fontSize = 12.sp)
            if (visible.isNotEmpty()) {
                Row(Modifier.fillMaxWidth().height(18.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    visible.forEachIndexed { index, (_, amount) ->
                        Box(Modifier.weight(amount.toFloat().coerceAtLeast(1f)).fillMaxHeight().background(CategoryColors[index % CategoryColors.size], RoundedCornerShape(5.dp)))
                    }
                }
                visible.take(6).forEachIndexed { index, (category, amount) ->
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(10.dp).background(CategoryColors[index % CategoryColors.size], RoundedCornerShape(3.dp)))
                        Text(categoryLabel(category), color = Ink, fontSize = 12.sp, modifier = Modifier.weight(1f).padding(start = 8.dp))
                        Text("${percent(amount, total)}%", color = Muted, fontSize = 11.sp)
                        Text(clp(amount), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                val top = visible.first()
                val topLimit = limits[top.first]
                if (topLimit != null && top.second > topLimit) Text("Tu mayor área (${categoryLabel(top.first)}) supera su límite en ${clp(top.second - topLimit)}.", color = Color(0xFFB3261E), fontSize = 12.sp)
            } else {
                Text("Registra o captura gastos para ver qué área concentra tu presupuesto.", color = Muted, fontSize = 12.sp)
            }
            if (uncategorized > 0) Text("${clp(uncategorized)} están clasificados como Otros.", color = Color(0xFF9A6500), fontSize = 12.sp)
        }
    }
}

@Composable private fun CategoryLimitRow(category: String, spent: Int, limit: Int) {
    val ratio = if (limit > 0) spent.toFloat() / limit else 0f
    val exceeded = limit > 0 && spent > limit
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(categoryLabel(category), color = Ink, fontSize = 12.sp)
            Text(if (limit > 0) "${clp(spent)} / ${clp(limit)}" else "${clp(spent)} · sin límite", color = if (exceeded) Color(0xFFB3261E) else Muted, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        }
        LinearProgressIndicator(progress = { ratio.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(7.dp), color = if (exceeded) Color(0xFFB3261E) else Green, trackColor = Color(0xFFE3EEE9))
    }
}

@Composable private fun SpendingPaceCard(total: Int, previousTotal: Int, dailyAverage: Int, dailySafeAmount: Int, available: Int) {
    val change = if (previousTotal > 0) ((total - previousTotal).toFloat() / previousTotal * 100).toInt() else null
    Card(shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF6F1))) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Ritmo de gasto", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text("Usa el ritmo actual para anticiparte al cierre del mes.", color = Muted, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Promedio diario", color = Muted, fontSize = 12.sp)
                Text(clp(dailyAverage), color = Ink, fontWeight = FontWeight.Bold)
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Disponible diario restante", color = Muted, fontSize = 12.sp)
                Text(clp(dailySafeAmount), color = if (available >= 0) Green else Color(0xFFB3261E), fontWeight = FontWeight.Bold)
            }
            change?.let { Text("${if (it >= 0) "+" else ""}$it% frente al mes anterior (${clp(previousTotal)}).", color = if (it > 0) Color(0xFF9A6500) else Green, fontSize = 12.sp) }
            if (available < 0) Text("El ritmo actual ya excede lo planificado.", color = Color(0xFFB3261E), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable private fun BreakdownCard(title: String, rows: List<Pair<String, Int>>, emptyLabel: String) {
    val visibleRows = rows.filter { it.second > 0 }.sortedByDescending { it.second }
    Card(shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Text(title, color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            if (visibleRows.isEmpty()) {
                Text(emptyLabel, color = Muted, fontSize = 12.sp)
            } else {
                val maximum = visibleRows.maxOf { it.second }.coerceAtLeast(1)
                visibleRows.take(6).forEach { (label, amount) ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(label, color = Ink, fontSize = 12.sp)
                            Text(clp(amount), color = Ink, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        }
                        LinearProgressIndicator(
                            progress = { amount.toFloat() / maximum },
                            modifier = Modifier.fillMaxWidth().height(7.dp),
                            color = Green,
                            trackColor = Color(0xFFE3EEE9)
                        )
                    }
                }
            }
        }
    }
}

@Composable private fun SummaryMetric(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, valueColor: Color, modifier: Modifier = Modifier) {
    Card(modifier = modifier, shape = RoundedCornerShape(16.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Card(shape = RoundedCornerShape(50), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4F0))) {
                Icon(icon, contentDescription = null, tint = Green, modifier = Modifier.padding(7.dp).size(20.dp))
            }
            Text(label, color = Muted, fontSize = 11.sp, maxLines = 1)
            Text(value, color = valueColor, fontSize = 14.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    emphasized: Boolean = false,
    enabled: Boolean = true
) {
    Row(
        Modifier
            .fillMaxWidth()
            .sizeIn(minHeight = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .alpha(if (enabled) 1f else 0.6f)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Card(shape = RoundedCornerShape(12.dp), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), colors = CardDefaults.cardColors(containerColor = if (emphasized) Mint else Lavender)) {
            Icon(icon, contentDescription = null, tint = Green, modifier = Modifier.padding(9.dp).size(21.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(title, color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
            Text(subtitle, color = Muted, fontSize = 11.sp)
        }
        Text("›", color = Green, fontSize = 24.sp, fontWeight = FontWeight.Light)
    }
}

@Composable private fun SummaryMovementRow(event: CapturedEvent) {
    Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
        Card(shape = RoundedCornerShape(50), elevation = CardDefaults.cardElevation(defaultElevation = 1.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEAF4F0))) {
            Icon(
                imageVector = when (event.category) { "supermercado" -> Icons.Filled.ShoppingCart; "auto" -> Icons.Filled.DirectionsCar; "suscripciones" -> Icons.Filled.PlayArrow; else -> Icons.Filled.Description },
                contentDescription = null,
                tint = Green,
                modifier = Modifier.padding(10.dp).size(20.dp)
            )
        }
        Column(Modifier.weight(1f).padding(start = 10.dp)) {
            Text(event.merchant ?: "Movimiento", color = Ink, fontWeight = FontWeight.SemiBold)
            Text("${event.categoryLabel()} · ${event.receivedAtShortLabel()}", color = Muted, fontSize = 12.sp)
        }
        Text(clp(event.amount ?: 0), color = Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun CapturedEventRow(event: CapturedEvent, onReview: () -> Unit = {}) {
    Card(shape = RoundedCornerShape(18.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Card(shape = RoundedCornerShape(50), colors = CardDefaults.cardColors(containerColor = if (event.status == "requires_review") Color(0xFFFFF0D6) else Mint)) {
                    Icon(if (event.status == "requires_review") Icons.Filled.Warning else Icons.Filled.Description, contentDescription = null, tint = if (event.status == "requires_review") Color(0xFF9A6500) else Green, modifier = Modifier.padding(10.dp).size(20.dp))
                }
                Column(Modifier.weight(1f)) {
                    Text(event.merchant ?: "Movimiento sin comercio", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("${categoryLabel(event.category)} · ${event.receivedAtShortLabel()}", color = Muted, fontSize = 12.sp)
                }
                Text(clp(event.amount ?: 0), color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            if (event.status == "requires_review") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onReview) { Text("Marcar como revisado", color = Green, fontSize = 12.sp) }
                }
            } else {
                Text(event.statusLabel(), color = Green, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable private fun CapturedExpenseRow(event: CapturedEvent, onEditCategory: () -> Unit, onConfirm: () -> Unit) {
    Card(shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(event.merchant ?: "Comercio pendiente", color = Ink, fontWeight = FontWeight.SemiBold); Text("${event.categoryLabel()} · ${event.movementTypeLabel()} · ${event.paymentSourceLabel()} · ${event.receivedAtShortLabel()}", color = Muted, fontSize = 12.sp) }
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
private fun categoryLabel(category: String) = when (category) {
    "alimentacion", "supermercado" -> "Alimentación"
    "vivienda", "casa" -> "Vivienda"
    "transporte", "auto" -> "Transporte"
    "salud" -> "Salud"
    "servicios" -> "Servicios"
    "ocio", "salidas" -> "Ocio"
    "compras" -> "Compras"
    "deudas" -> "Deudas"
    else -> "Otros"
}
private fun String.normalizedBudgetCategory() = when (this) {
    "supermercado", "alimentacion" -> "alimentacion"
    "casa", "vivienda" -> "vivienda"
    "auto", "transporte" -> "transporte"
    "salidas", "ocio" -> "ocio"
    else -> this
}

private fun chatGptBrief(
    budget: Int,
    total: Int,
    expenses: List<CapturedEvent>,
    pending: Int,
    reconciled: Int
): String = buildString {
    appendLine("Actúa como analista de finanzas personales para Brújula.")
    appendLine("Este resumen fue generado por el usuario y no contiene credenciales ni texto bancario original.")
    appendLine()
    appendLine("DATOS DEL MES")
    appendLine("Presupuesto: ${clp(budget)}")
    appendLine("Gasto registrado: ${clp(total)}")
    appendLine("Disponible: ${clp((budget - total).coerceAtLeast(0))}")
    appendLine("Conciliados: $reconciled")
    appendLine("Por revisar: $pending")
    appendLine()
    appendLine("MOVIMIENTOS RESUMIDOS")
    expenses.sortedByDescending { it.receivedAt }.take(20).forEach { event ->
        appendLine("- ${event.merchant ?: "Comercio sin nombre"} | ${event.categoryLabel()} | ${clp(event.amount ?: 0)} | ${event.paymentSourceLabel()}")
    }
    appendLine()
    appendLine("RESPONDE")
    appendLine("1. Resume los patrones principales sin inventar información.")
    appendLine("2. Indica qué movimientos debería revisar.")
    appendLine("3. Sugiere límites razonables para el próximo mes y explica el criterio.")
    appendLine("4. Separa observaciones de recomendaciones y pide confirmación antes de proponer cambios.")
}
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

private fun Long.isPreviousMonth(): Boolean {
    val event = Calendar.getInstance().apply { timeInMillis = this@isPreviousMonth }
    val previous = Calendar.getInstance().apply { add(Calendar.MONTH, -1) }
    return event.get(Calendar.YEAR) == previous.get(Calendar.YEAR) && event.get(Calendar.MONTH) == previous.get(Calendar.MONTH)
}

private fun Long.isMonth(year: Int, month: Int): Boolean {
    val event = Calendar.getInstance().apply { timeInMillis = this@isMonth }
    return event.get(Calendar.YEAR) == year && event.get(Calendar.MONTH) == month
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
                put("category_manual", event.categoryManual)
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
            status = status,
            categoryManual = event.optBoolean("category_manual", false)
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
