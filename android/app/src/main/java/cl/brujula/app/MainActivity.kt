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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
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
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper

private val BudgetCategories = listOf("alimentacion", "vivienda", "transporte", "salud", "servicios", "ocio", "compras", "deudas", "otros")
private val CategoryColors = listOf(Green, Coral, Ochre, Color(0xFF6C8E84), Color(0xFF8C6A57), Color(0xFFB86A6A), Color(0xFF7A9A72), Color(0xFF8B7767), Color(0xFF9CA9A4))

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Green.toArgb()
        window.navigationBarColor = Paper.toArgb()
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = true
        }
        setContent { BrujulaApp(this) }
    }
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
    var editingEventId by rememberSaveable { mutableStateOf<Long?>(null) }
    val editingEvent = captured.firstOrNull { it.id == editingEventId }
    var ignoringEvent by remember { mutableStateOf<CapturedEvent?>(null) }
    var showManualDialog by rememberSaveable { mutableStateOf(false) }
    var importStatus by remember { mutableStateOf<String?>(null) }
    var statementStatus by remember { mutableStateOf<String?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showIncomeDialog by rememberSaveable { mutableStateOf(false) }
    var showCommitmentDialog by rememberSaveable { mutableStateOf(false) }
    var showGoalDialog by rememberSaveable { mutableStateOf(false) }
    var showCategoryLimitDialog by rememberSaveable { mutableStateOf(false) }
    var planningVersion by remember { mutableIntStateOf(0) }
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var movementFilter by rememberSaveable { mutableStateOf("Todos") }
    var movementQuery by rememberSaveable { mutableStateOf("") }
    var movementPayment by rememberSaveable { mutableStateOf("Todos") }
    var calendarMonth by rememberSaveable { mutableIntStateOf(Calendar.getInstance().get(Calendar.MONTH)) }
    var calendarYear by rememberSaveable { mutableIntStateOf(Calendar.getInstance().get(Calendar.YEAR)) }
    var selectedCalendarDay by rememberSaveable { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }
    var movementDay by rememberSaveable { mutableStateOf<String?>(null) }
    var calendarRequested by rememberSaveable { mutableStateOf(false) }
    var editingIncomeId by rememberSaveable { mutableStateOf<String?>(null) }
    var editingCommitmentId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePlanningId by rememberSaveable { mutableStateOf<String?>(null) }
    var deletePlanningKind by rememberSaveable { mutableStateOf("income") }
    val movementListState = rememberLazyListState()
    val statementLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val imported = runCatching {
            PDFBoxResourceLoader.init(activity.applicationContext)
            val text = activity.contentResolver.openInputStream(uri)?.use { input ->
                PDDocument.load(input).use { document -> PDFTextStripper().apply { sortByPosition = true }.getText(document) }
            }.orEmpty()
            val year = Regex("(?:19|20)[0-9]{2}").find(text)?.value?.toIntOrNull() ?: Calendar.getInstance().get(Calendar.YEAR)
            val rows = ScotiaStatementPdfParser.parse(text, year)
            val store = CapturedEventStore(activity)
            rows.count { row ->
                store.insertStatementEvent(
                    receivedAt = row.receivedAt,
                    rawText = "Scotiabank PDF | ${row.movementType} | ${row.merchant} | ${row.amount}" +
                        (row.installment?.let { " | cuota $it" } ?: "") + " | fila ${row.occurrence}",
                    amount = row.amount,
                    merchant = row.merchant,
                    movementType = row.movementType,
                    category = row.category,
                    paymentSource = row.paymentSource
                )
            }
        }.getOrElse {
            statementStatus = "No se pudo leer la cartola PDF. Verifica que sea una cartola Scotiabank con texto seleccionable."
            return@rememberLauncherForActivityResult
        }
        val store = CapturedEventStore(activity)
        val reconciled = store.reconcileStatements()
        captured = store.recent(10_000)
        statementStatus = if (imported == 0) "No se encontraron movimientos nuevos en la cartola PDF." else "Cartola PDF: $imported movimientos nuevos; $reconciled conciliados automáticamente. Los demás requieren revisión."
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
            val store = CapturedEventStore(activity)
            writer.write(syncBundleJson(store.recent(10_000, includeIgnored = true), PlanningStore(activity).exportJson(), store))
        }
        syncStatus = "Paquete de sincronización exportado."
    }
    val syncImportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        val json = activity.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
        val imported = importSyncBundle(activity, json)
        planningVersion++
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
                planningVersion++
                captured = CapturedEventStore(activity).recent(10_000)
                updateChecker.resumePendingInstall { updateStatus = it }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val realCaptured = captured.filter { it.isLedgerEvent }
    val pendingReconciliation = realCaptured.count { it.isPending }
    val reconciledCount = captured.count { it.status == "reconciled" }
    val expenses = realCaptured.filter { it.movementType == "expense" }
    val today = Calendar.getInstance()
    val thisYear = today.get(Calendar.YEAR)
    val thisMonth = today.get(Calendar.MONTH)
    val previous = (today.clone() as Calendar).apply { add(Calendar.MONTH, -1) }
    val monthExpenses = expenses.filter { it.monthlyExpense(thisYear, thisMonth) > 0 }
    val previousMonthExpenses = expenses.filter { it.monthlyExpense(previous.get(Calendar.YEAR), previous.get(Calendar.MONTH)) > 0 }
    val monthRefunds = realCaptured.filter { it.isConfirmed && it.movementType == "refund" && it.receivedAt.isCurrentMonth() }
    // The purchase is one obligation; monthly charges, not its full total, feed the cash-flow budget.
        val total = monthExpenses.sumOf { it.monthlyExpense(thisYear, thisMonth) }
    val calendarExpenses = expenses.filter { it.receivedAt.isMonth(calendarYear, calendarMonth) }
    val previousTotal = previousMonthExpenses.filter { it.isConfirmed }.sumOf { it.monthlyExpense(previous.get(Calendar.YEAR), previous.get(Calendar.MONTH)) }
    val refunds = monthRefunds.sumOf { it.amount ?: 0 }
    val netTotal = (monthExpenses.filter { it.isConfirmed }.sumOf { it.monthlyExpense(thisYear, thisMonth) } - refunds).coerceAtLeast(0)
        val thisMonthPurchases = expenses.filter { it.isConfirmed && it.receivedAt.isCurrentMonth() }
    val planningStore = remember(planningVersion) { PlanningStore(activity) }
    val categoryTotals = BudgetCategories
        .map { category -> category to monthExpenses.filter { it.isConfirmed && it.category.normalizedBudgetCategory() == category }.sumOf { it.monthlyExpense(thisYear, thisMonth) } }
    val paymentTotals = listOf("tarjeta_credito", "cuenta_corriente", "manual", "cartola")
        .map { source -> source to monthExpenses.filter { it.isConfirmed && it.paymentSource == source }.sumOf { it.monthlyExpense(thisYear, thisMonth) } }
    val categoryLimits = planningStore.categoryLimits().associate { it.category.normalizedBudgetCategory() to it.amount }
    val uncategorizedTotal = monthExpenses.filter { it.category.normalizedBudgetCategory() == "otros" }.sumOf { it.monthlyExpense(thisYear, thisMonth) }
    val plannedIncomeTotal = planningStore.incomes().sumOf { it.amount }
    val monthlyBudget = plannedIncomeTotal
    val activeCommitments = planningStore.commitments().filter { it.isActive }
    val automaticInstallmentTotal = monthExpenses.filter { it.installmentCount > 1 }.sumOf { it.monthlyExpense(thisYear, thisMonth) }
    val plannedCommitmentTotal = activeCommitments.sumOf { it.amount } + automaticInstallmentTotal
    val monthlyGoal = planningStore.monthlyGoal()
    val plannedAvailable = plannedIncomeTotal - plannedCommitmentTotal - monthlyGoal
    // An automatic installment appears in both spent and commitments; deduct it only once.
    val budgetSnapshot = monthlyBudget(realCaptured, plannedIncomeTotal, activeCommitments.sumOf { it.amount }, monthlyGoal, thisYear, thisMonth)
    val pendingReserved = budgetSnapshot.pendingReserved
    val realAvailable = budgetSnapshot.available
    val daysElapsed = today.get(Calendar.DAY_OF_MONTH)
    val daysInMonth = today.getActualMaximum(Calendar.DAY_OF_MONTH)
    val dailyAverage = if (daysElapsed > 0) netTotal / daysElapsed else 0
    val dailySafeAmount = if (daysElapsed < daysInMonth) (realAvailable.coerceAtLeast(0) / (daysInMonth - daysElapsed).coerceAtLeast(1)) else 0
    val projections = planningStore.projections(months = 12).mapIndexed { offset, projection ->
        val target = (today.clone() as Calendar).apply { add(Calendar.MONTH, offset) }
        val year = target.get(Calendar.YEAR)
        val month = target.get(Calendar.MONTH)
        val active = expenses.filter { it.installmentCount > 1 && it.monthlyExpense(year, month) > 0 }
        projection.copy(
            commitmentTotal = projection.commitmentTotal + active.sumOf { it.monthlyExpense(year, month) },
            activeCommitments = projection.activeCommitments + active.size
        )
    }
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0 && calendarRequested) {
            val index = 5 + (if (plannedIncomeTotal > 0) 1 else 0) + (if (pendingReconciliation > 0 || uncategorizedTotal > 0) 1 else 0)
            movementListState.scrollToItem(index)
            calendarRequested = false
        } else movementListState.scrollToItem(0)
    }
    MaterialTheme(
        colorScheme = lightColorScheme(
            primary = Green,
            onPrimary = Color.White,
            secondary = Coral,
            secondaryContainer = Mint,
            onSecondaryContainer = Green,
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
        Surface(color = Green, modifier = Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().background(Paper)) {
            BrandHeader(onAccount = { selectedTab = 4 })
            LazyColumn(
                state = movementListState,
                modifier = Modifier.weight(1f).testTag("main-list"),
                contentPadding = PaddingValues(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                if (selectedTab == 0) {
                    item {
                        EditorialHeading(SimpleDateFormat("EEEE d / MMMM", Locale.forLanguageTag("es-CL")).format(Date()), "Tu dinero, con rumbo.")
                    }
                    item { SummaryHeroCard(realAvailable, netTotal, plannedIncomeTotal, pendingReserved, pendingReconciliation) }
                    item { MonthMetrics(netTotal, monthlyGoal) }
                    if (plannedIncomeTotal > 0) item { BudgetDistribution(netTotal, monthlyGoal, realAvailable, pendingReserved, activeCommitments.sumOf { it.amount }) }
                    item {
                        Button(onClick = { showManualDialog = true }, modifier = Modifier.fillMaxWidth().heightIn(min = 58.dp),
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Ink),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)) {
                            Text("＋  Agregar gasto", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                    item {
                        Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    Text("Actividad reciente", color = Ink, fontSize = 21.sp, fontFamily = FontFamily.Serif, modifier = Modifier.weight(1f))
                                    TextButton(onClick = { movementFilter = "Todos"; movementQuery = ""; movementDay = null; movementPayment = "Todos"; selectedTab = 1 }) { Text("Ver todo", color = Green) }
                                }
                                val recentSummary = monthExpenses.sortedByDescending { it.receivedAt }.take(3)
                                if (recentSummary.isEmpty()) Text("Aún no hay gastos reales. Agrega el primero o configura la captura desde Cuenta.", color = Muted, fontSize = 13.sp)
                                else recentSummary.forEach { event -> SummaryMovementRow(event) { editingEventId = event.id } }
                            }
                        }
                    }
                    if (pendingReconciliation > 0 || uncategorizedTotal > 0) item {
                        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Lavender),
                            modifier = Modifier.fillMaxWidth().clickable(role = Role.Button) { movementFilter = "Pendientes"; movementQuery = ""; movementDay = null; movementPayment = "Todos"; selectedTab = 1 }) {
                            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                Text("Hay decisiones pendientes", color = Ink, fontWeight = FontWeight.Bold)
                                if (pendingReconciliation > 0) Text("$pendingReconciliation movimientos requieren revisión.", color = Muted, fontSize = 12.sp)
                                if (uncategorizedTotal > 0) Text("${clp(uncategorizedTotal)} están en Otros.", color = Muted, fontSize = 12.sp)
                                TextButton(onClick = { movementFilter = "Pendientes"; movementQuery = ""; movementDay = null; movementPayment = "Todos"; selectedTab = 1 }) { Text("Abrir pendientes →", color = Green) }
                            }
                        }
                    }
                    item {
                        CalendarSpendingCard(expenses = calendarExpenses, month = calendarMonth, year = calendarYear, selectedDay = selectedCalendarDay,
                            onMonthChange = { delta ->
                                val next = Calendar.getInstance().apply { set(calendarYear, calendarMonth, 1); add(Calendar.MONTH, delta) }
                                calendarMonth = next.get(Calendar.MONTH); calendarYear = next.get(Calendar.YEAR); selectedCalendarDay = 1
                            }, onDaySelected = { selectedCalendarDay = it },
                            onViewMovements = { movementQuery = ""; movementFilter = "Todos"; movementPayment = "Todos"; movementDay = "%04d-%02d-%02d".format(calendarYear, calendarMonth + 1, selectedCalendarDay); selectedTab = 1 })
                    }
                    item { BehaviorChartCard(thisMonthPurchases, thisMonthPurchases.sumOf { it.amount ?: 0 }, if (daysElapsed > 0) thisMonthPurchases.sumOf { it.amount ?: 0 } / daysElapsed else 0) }
                    item { CategoryDistributionCard(categoryTotals, netTotal, categoryLimits, uncategorizedTotal) }
                    item { SpendingPaceCard(netTotal, previousTotal, dailyAverage, dailySafeAmount, realAvailable) }
                    item { BreakdownCard("Gasto por medio de pago", paymentTotals.map { (source, amount) -> source.replace("_", " ").replaceFirstChar { it.uppercase() } to amount }, "Todavía no hay gastos con medio de pago identificado.") }
                    item {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SummaryMetric(Icons.Filled.AccountBalanceWallet, "Presupuesto", clp(plannedAvailable.coerceAtLeast(0)), Green, Modifier.weight(1f))
                            SummaryMetric(Icons.Filled.Check, "Conciliación", reconciledCount.toString(), Green, Modifier.weight(1f))
                            SummaryMetric(Icons.Filled.Description, "Por revisar", pendingReconciliation.toString(), Green, Modifier.weight(1f))
                        }
                    }
                }
                if (selectedTab == 1) {
                    val visibleMovements = realCaptured.filter { event ->
                        movementMatches(event, movementQuery, movementFilter, movementPayment, movementDay)
                    }
                    item {
                        EditorialHeading("Cada movimiento cuenta", "Movimientos", "Revisa, clasifica y confirma cada gasto.")
                        movementDay?.let { day ->
                            InputChip(selected = true, onClick = { movementDay = null }, label = { Text("Fecha: $day · Quitar filtro") })
                        }
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
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Todos", "Pendientes", "Confirmados").forEach { filter ->
                                FilterChip(
                                    selected = movementFilter == filter,
                                    onClick = { movementFilter = filter },
                                    label = { Text(filter) }
                                )
                            }
                        }
                        Text("Medio de pago", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
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
                            TextButton(onClick = { calendarRequested = true; selectedTab = 0 }) { Text("Ver calendario", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                    }
                    if (visibleMovements.isEmpty()) {
                        item { Text("No hay movimientos que coincidan con esta búsqueda.", color = Muted, fontSize = 13.sp) }
                    } else {
                        items(visibleMovements) { event ->
                            if (event.isPending) {
                                CapturedExpenseRow(
                                    event = event,
                                    onEditCategory = { editingEventId = event.id },
                                    onIgnore = { ignoringEvent = event },
                                    onConfirm = {
                                        if (!CapturedEventStore(activity).confirmMovement(event.id)) editingEventId = event.id
                                        captured = CapturedEventStore(activity).recent(10_000)
                                    }
                                )
                            } else {
                                CapturedEventRow(event, onEdit = { editingEventId = event.id }, onIgnore = { ignoringEvent = event })
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
                        EditorialHeading("Planifica con calma", "Planificación", "Tu futuro, a la vista.")
                        Spacer(Modifier.height(6.dp))
                        PlanHeroCard(plannedIncomeTotal, plannedCommitmentTotal, monthlyGoal, plannedAvailable)
                        Spacer(Modifier.height(18.dp))
                        InstallmentOverview(expenses)
                        Text("Acciones rápidas", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 10.dp))
                        Row(Modifier.fillMaxWidth().padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            PlanActionTile(Icons.Filled.AccountBalanceWallet, "Ingreso", { editingIncomeId = null; showIncomeDialog = true }, Modifier.weight(1f))
                            PlanActionTile(Icons.Filled.Description, "Compromiso", { editingCommitmentId = null; showCommitmentDialog = true }, Modifier.weight(1f))
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
                                    projections.take(12).forEach { projection ->
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
                                PlanningRow("${income.name} · ${clp(income.amount)}", "Ingreso recurrente", onEdit = { editingIncomeId = income.id; showIncomeDialog = true }) {
                                    deletePlanningKind = "income"; deletePlanningId = income.id
                                }
                            }
                        } else {
                            Text("Aún no has registrado ingresos. Agrégalos para calcular tu disponible.", color = Muted, fontSize = 12.sp)
                        }
                        if (planningStore.commitments().isNotEmpty() || expenses.any { it.installmentCount > 1 }) {
                            Spacer(Modifier.height(12.dp))
                            Text("Tus compromisos", color = Ink, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            planningStore.commitments().forEach { commitment ->
                                val installment = when { !commitment.hasInstallments -> "Recurrente"; commitment.currentNumber < 1 -> "Comienza en ${commitment.firstInstallmentMonth}"; commitment.currentNumber > commitment.totalInstallments -> "Finalizado"; else -> "Cuota ${commitment.currentNumber}/${commitment.totalInstallments}" }
                                PlanningRow("${commitment.name} · ${clp(commitment.amount)}", installment, onEdit = { editingCommitmentId = commitment.id; showCommitmentDialog = true }) {
                                    deletePlanningKind = "commitment"; deletePlanningId = commitment.id
                                }
                            }
                            expenses.filter { it.installmentCount > 1 }.forEach { expense ->
                                InstallmentPurchaseRow(expense, expense.monthlyExpense(thisYear, thisMonth), onEdit = { editingEventId = expense.id })
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
                        Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 6.dp)) {
                                ActionRow(Icons.Filled.Person, "Cuenta cloud", "Recuperar movimientos en otro teléfono", { showCloudAccountDialog = true })
                                if (cloudAccountLabel != "Sin cuenta cloud") {
                                    ActionRow(Icons.Filled.Check, if (syncing) "Sincronizando…" else "Sincronizar ahora", "Enviar y recuperar movimientos", {
                                        if (!syncing) {
                                            syncing = true
                                            syncStatus = "Sincronizando…"
                                            firebaseSync.sync(CapturedEventStore(activity).recent(10_000)) { result ->
                                                syncStatus = result
                                                captured = CapturedEventStore(activity).recent(10_000)
                                                planningVersion++
                                                syncing = false
                                            }
                                        }
                                    }, emphasized = true, enabled = !syncing)
                                }
                                ActionRow(Icons.Filled.Description, "Respaldo portátil", "Exportar o importar un archivo local", {
                                    syncExportLauncher.launch("brujula-sync-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.json")
                                })
                                ActionRow(Icons.Filled.Description, "Importar cartola Scotiabank PDF", "Guardar movimientos para revisar y conciliar", {
                                    statementLauncher.launch(arrayOf("application/pdf"))
                                })
                                statementStatus?.let { Text(it, color = Green, fontSize = 12.sp, modifier = Modifier.padding(vertical = 8.dp)) }
                                ActionRow(Icons.Filled.List, "Importar respaldo", "Seleccionar un archivo existente", {
                                    syncImportLauncher.launch(arrayOf("application/json", "text/json", "text/*"))
                                })
                            }
                        }
                        syncStatus?.let { Text(it, color = Green, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
                        Text("Preferencias", color = Green, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
                        Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
                                CapturedEventStore(activity).confirmMovement(event.id)
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
                                onEditCategory = { editingEventId = event.id },
                                onIgnore = { ignoringEvent = event },
                                onConfirm = {
                                    CapturedEventStore(activity).confirmMovement(event.id)
                                    captured = CapturedEventStore(activity).recent(10_000)
                                }
                            )
                        }
                    }
                }
            }
            Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 10.dp),
                shape = RoundedCornerShape(26.dp), shadowElevation = 6.dp, color = Color.White) {
            NavigationBar(containerColor = Color.White, tonalElevation = 0.dp, windowInsets = WindowInsets(0, 0, 0, 0)) {
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
            }
        deletePlanningId?.let { id ->
            AlertDialog(onDismissRequest = { deletePlanningId = null }, title = { Text("Quitar del plan") },
                text = { Text("Se quitará este elemento de la planificación. Los movimientos registrados se conservarán.") },
                confirmButton = { TextButton(onClick = {
                    if (deletePlanningKind == "income") planningStore.deleteIncome(id) else planningStore.deleteCommitment(id)
                    planningVersion++; deletePlanningId = null
                }) { Text("Quitar") } }, dismissButton = { TextButton(onClick = { deletePlanningId = null }) { Text("Cancelar") } })
        }
        if (showManualDialog) {
            var manualMerchant by rememberSaveable { mutableStateOf("") }
            var manualAmount by rememberSaveable { mutableStateOf("") }
            var manualCategory by rememberSaveable { mutableStateOf("otros") }
            var manualSource by rememberSaveable { mutableStateOf("cuenta_corriente") }
            var manualError by remember { mutableStateOf<String?>(null) }
            var manualDate by rememberSaveable { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())) }
            var manualFinanced by rememberSaveable { mutableStateOf(false) }
            var manualParts by rememberSaveable { mutableStateOf("2") }
            var manualFirstMonth by rememberSaveable { mutableStateOf(currentMonthKey()) }
            AlertDialog(
                onDismissRequest = { showManualDialog = false },
                title = { Text("Agregar gasto manual") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualMerchant,
                            onValueChange = { manualMerchant = it },
                            label = { Text("Comercio o descripción") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = manualAmount,
                            onValueChange = { manualAmount = it },
                            label = { Text("Monto total en pesos") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                        )
                        TextButton(onClick = { chooseDate(activity, manualDate) { manualDate = it } }) { Text("Fecha: $manualDate · Cambiar") }
                        Text("Medio de pago", fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("cuenta_corriente" to "Cuenta corriente", "tarjeta_credito" to "Tarjeta").forEach { (value, label) ->
                                FilterChip(
                                    selected = manualSource == value,
                                    onClick = { manualSource = value },
                                    label = { Text(label) }
                                )
                            }
                        }
                        Text("Categoría", fontWeight = FontWeight.SemiBold)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            BudgetCategories.map { it to categoryLabel(it) }.forEach { (value, label) ->
                                FilterChip(
                                    selected = manualCategory == value,
                                    onClick = { manualCategory = value },
                                    label = { Text(label) }
                                )
                            }
                        }
                        if (manualSource == "tarjeta_credito") {
                            Row(Modifier.horizontalScroll(rememberScrollState())) {
                                FilterChip(selected = !manualFinanced, onClick = { manualFinanced = false }, label = { Text("Una cuota") })
                                FilterChip(selected = manualFinanced, onClick = { manualFinanced = true }, label = { Text("En cuotas") })
                            }
                            if (manualFinanced) {
                                OutlinedTextField(manualParts, { manualParts = it }, label = { Text("Número de cuotas") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                                OutlinedTextField(manualFirstMonth, { manualFirstMonth = it }, label = { Text("Primer cargo (AAAA-MM)") }, singleLine = true)
                                val parts = manualParts.toIntOrNull()
                                val value = parseClpInput(manualAmount)
                                if (parts != null && parts in 2..120 && value != null && value >= parts) Text("Cuota aproximada: ${clp(value / parts)}. El total no se descuenta completo del mes.", color = Muted, fontSize = 12.sp)
                            }
                        }
                        manualError?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val amount = parseClpInput(manualAmount)
                        val date = parseDateInput(manualDate)
                        val parts = if (manualFinanced && manualSource == "tarjeta_credito") manualParts.toIntOrNull() else 1
                        when {
                            manualMerchant.isBlank() -> manualError = "Escribe una descripción."
                            amount == null || amount <= 0 -> manualError = "Ingresa un monto válido."
                            date == null -> manualError = "Revisa la fecha."
                            parts == null || parts !in 1..120 || parts > amount || (parts > 1 && !validMonth(manualFirstMonth)) -> manualError = "Revisa las cuotas y el primer mes de cargo."
                            else -> {
                                CapturedEventStore(activity).insertManualExpense(
                                    merchant = manualMerchant,
                                    amount = amount,
                                    category = manualCategory,
                                    paymentSource = manualSource, receivedAt = date,
                                    installmentCount = parts, firstInstallmentMonth = if (parts > 1) manualFirstMonth else null
                                )
                                captured = CapturedEventStore(activity).recent(10_000)
                                showManualDialog = false
                            }
                        }
                    }) { Text("Guardar y confirmar") }
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
                        Text("Es la reserva mensual de ahorro. Se resta del disponible para gastar.", color = Muted, fontSize = 12.sp)
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
            val original = planningStore.incomes().firstOrNull { it.id == editingIncomeId }
            var name by rememberSaveable(editingIncomeId) { mutableStateOf(original?.name.orEmpty()) }
            var amountInput by rememberSaveable(editingIncomeId) { mutableStateOf(original?.amount?.toString().orEmpty()) }
            var error by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { showIncomeDialog = false },
                title = { Text(if (editingIncomeId == null) "Agregar ingreso" else "Editar ingreso") },
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
                        else { planningStore.addIncome(name, amount, id = editingIncomeId); planningVersion++; showIncomeDialog = false }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { showIncomeDialog = false }) { Text("Cancelar") } }
            )
        }
        if (showCommitmentDialog) {
            val original = planningStore.commitments().firstOrNull { it.id == editingCommitmentId }
            var name by rememberSaveable(editingCommitmentId) { mutableStateOf(original?.name.orEmpty()) }
            var amountInput by rememberSaveable(editingCommitmentId) { mutableStateOf(original?.amount?.toString().orEmpty()) }
            var firstMonthInput by rememberSaveable(editingCommitmentId) { mutableStateOf(original?.firstInstallmentMonth ?: currentMonthKey()) }
            var totalInput by rememberSaveable(editingCommitmentId) { mutableStateOf(original?.totalInstallments?.takeIf { it > 0 }?.toString().orEmpty()) }
            var category by rememberSaveable(editingCommitmentId) { mutableStateOf(original?.category ?: "servicios") }
            var error by remember { mutableStateOf<String?>(null) }
            AlertDialog(
                onDismissRequest = { showCommitmentDialog = false },
                title = { Text(if (editingCommitmentId == null) "Compromiso o cuota" else "Editar compromiso") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ej.: PC, diplomado, mamá") }, singleLine = true)
                        OutlinedTextField(value = amountInput, onValueChange = { amountInput = it }, label = { Text("Monto de la cuota") }, singleLine = true)
                        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(value = firstMonthInput, onValueChange = { firstMonthInput = it }, label = { Text("Primer mes (AAAA-MM)") }, singleLine = true)
                            OutlinedTextField(value = totalInput, onValueChange = { totalInput = it }, label = { Text("Total cuotas") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        }
                        Text("Categoría", fontWeight = FontWeight.SemiBold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
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
                        val total = if (totalInput.isBlank()) 0 else totalInput.toIntOrNull()
                        if (name.isBlank() || amount == null || amount <= 0 || total == null || total !in 0..120 || (total > 0 && !validMonth(firstMonthInput))) error = "Revisa nombre, monto, cuotas y primer mes."
                        else { planningStore.addCommitment(name, amount, 1, total, category, firstInstallmentMonth = if (total > 0) firstMonthInput else null, id = editingCommitmentId); planningVersion++; showCommitmentDialog = false }
                    }) { Text("Guardar") }
                },
                dismissButton = { TextButton(onClick = { showCommitmentDialog = false }) { Text("Cancelar") } }
            )
        }
        editingEvent?.let { event ->
            val history = remember(event.id) { CapturedEventStore(activity).history(event.id) }
            var merchant by rememberSaveable(event.id) { mutableStateOf(event.merchant.orEmpty()) }
            var amount by rememberSaveable(event.id) { mutableStateOf(event.amount?.toString().orEmpty()) }
            var date by rememberSaveable(event.id) { mutableStateOf(SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(event.receivedAt))) }
            var category by rememberSaveable(event.id) { mutableStateOf(event.category) }
            var payment by rememberSaveable(event.id) { mutableStateOf(event.paymentSource) }
            var financed by rememberSaveable(event.id) { mutableStateOf(event.installmentCount > 1) }
            var installments by rememberSaveable(event.id) { mutableStateOf(event.installmentCount.coerceAtLeast(2).toString()) }
            var firstChargeMonth by rememberSaveable(event.id) { mutableStateOf(event.firstInstallmentMonth ?: SimpleDateFormat("yyyy-MM", Locale.US).format(Date(event.receivedAt))) }
            var error by remember(event.id) { mutableStateOf<String?>(null) }
            fun saveMovement(confirm: Boolean) {
                    val parsedAmount = parseClpInput(amount)
                    val parsedDate = parseDateInput(date)
                    val count = if (financed && event.movementType == "expense") installments.toIntOrNull() else 1
                    val validMonth = Regex("[0-9]{4}-(0[1-9]|1[0-2])").matches(firstChargeMonth)
                    if (merchant.isBlank() || parsedAmount == null || parsedAmount <= 0 || parsedDate == null) error = "Revisa comercio, monto y fecha."
                    else if (count == null || count !in 1..120 || count > parsedAmount || (count > 1 && (!validMonth || payment != "tarjeta_credito"))) error = "Revisa número de cuotas, primer mes y medio de pago Tarjeta."
                    else {
                        CapturedEventStore(activity).updateMovement(event, merchant, parsedAmount, parsedDate, category, payment, count, if (count > 1) firstChargeMonth else null)
                        if (confirm) CapturedEventStore(activity).confirmMovement(event.id)
                        captured = CapturedEventStore(activity).recent(10_000)
                        editingEventId = null
                    }
            }
            AlertDialog(
                onDismissRequest = { editingEventId = null },
                title = { Text("Editar movimiento") },
                text = {
                    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                        OutlinedTextField(merchant, { merchant = it }, label = { Text("Comercio") }, singleLine = true)
                        OutlinedTextField(amount, { amount = it }, label = { Text("Monto total en pesos") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true)
                        TextButton(onClick = { chooseDate(activity, date) { date = it } }) { Text("Fecha: $date · Cambiar") }
                        Text("Categoría", fontWeight = FontWeight.SemiBold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            listOf("alimentacion", "vivienda", "transporte", "salud", "servicios", "ocio", "compras", "otros").forEach { value ->
                                FilterChip(category == value, { category = value }, label = { Text(categoryLabel(value), fontSize = 10.sp) })
                            }
                        }
                        Text("Medio de pago", fontWeight = FontWeight.SemiBold)
                        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            listOf("cuenta_corriente" to "Cuenta corriente", "tarjeta_credito" to "Tarjeta").forEach { (value, label) ->
                                FilterChip(payment == value, { payment = value }, label = { Text(label) })
                            }
                        }
                        error?.let { Text(it, color = MaterialTheme.colorScheme.error, fontSize = 12.sp) }
                        if (event.movementType == "expense") {
                            Text("Modalidad de compra", fontWeight = FontWeight.SemiBold)
                            Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                FilterChip(selected = !financed, onClick = { financed = false }, label = { Text("Una cuota") })
                                FilterChip(selected = financed, onClick = { financed = true; payment = "tarjeta_credito" }, label = { Text("En cuotas") })
                            }
                            if (financed) {
                                OutlinedTextField(installments, { installments = it }, label = { Text("Número total de cuotas") }, singleLine = true)
                                OutlinedTextField(firstChargeMonth, { firstChargeMonth = it }, label = { Text("Primer mes de cargo (AAAA-MM)") }, singleLine = true)
                                val parts = installments.toIntOrNull()
                                val amountValue = parseClpInput(amount)
                                if (parts != null && parts in 2..120 && amountValue != null && amountValue >= parts) {
                                    Text("Monto total ${clp(amountValue)} · cuota aproximada ${clp(amountValue / parts)}", color = Muted, fontSize = 12.sp)
                                }
                                Text("El total de la compra se conserva. Sus cargos mensuales se proyectan sin duplicar movimientos. Verifica el primer mes según tu tarjeta.", color = Muted, fontSize = 12.sp)
                            }
                        }
                        history.take(4).forEach { change -> Text("${change.field}: ${change.oldValue ?: "—"} → ${change.newValue ?: "—"}", color = Muted, fontSize = 10.sp) }
                    }
                },
                confirmButton = { TextButton(onClick = { saveMovement(event.isPending) }) { Text(if (event.isPending) "Guardar y confirmar" else "Guardar cambios") } },
                dismissButton = { Row {
                    if (event.isPending) TextButton(onClick = { saveMovement(false) }) { Text("Guardar pendiente") }
                    TextButton(onClick = { editingEventId = null }) { Text("Cancelar") }
                } }
            )
        }
        ignoringEvent?.let { event ->
            AlertDialog(onDismissRequest = { ignoringEvent = null }, title = { Text("Ignorar movimiento") },
                text = { Text("Se ocultará de los gastos y conciliaciones. El registro quedará conservado en el respaldo y la sincronización.") },
                confirmButton = { TextButton(onClick = { CapturedEventStore(activity).updateStatus(event.id, "ignored"); captured = CapturedEventStore(activity).recent(10_000); ignoringEvent = null }) { Text("Ignorar") } },
                dismissButton = { TextButton(onClick = { ignoringEvent = null }) { Text("Cancelar") } })
        }
    }
}

}

@Composable private fun PlanningRow(title: String, subtitle: String, onEdit: (() -> Unit)? = null, onDelete: () -> Unit) {
    Card(shape = RoundedCornerShape(13.dp), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, color = Ink, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = Muted, fontSize = 12.sp)
            }
            Column { onEdit?.let { TextButton(onClick = it) { Text("Editar") } }; TextButton(onClick = onDelete) { Text("Quitar") } }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun PlanHeroCard(income: Int, commitments: Int, goal: Int, available: Int) {
    GradientHero {
        Text("TU PLAN DEL MES", color = Mint, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
        Text(clp(available), color = if (available >= 0) Paper else Lavender, fontSize = 36.sp, fontFamily = FontFamily.Serif)
        Text(if (available >= 0) "Disponible antes de los gastos del mes" else "Los compromisos y la meta superan los ingresos", color = Mint, fontSize = 12.sp)
        HorizontalDivider(color = Mint.copy(alpha = .25f))
        FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            PlanMetric("Ingresos", income); PlanMetric("Compromisos", commitments); PlanMetric("Meta", goal)
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
                    Text("Compras realizadas por día", color = Ink, fontSize = 18.sp, fontWeight = FontWeight.Bold)
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
                CalendarInsight("Día más caro", mostExpensive?.let { "${it.key} ${monthLabel.substringBefore(" ")} · ${clp(it.value)}" } ?: "Sin gastos", Icons.Filled.Warning, Modifier.weight(1f))
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
                    Text("Compras registradas", color = Color.White, fontSize = 17.sp, fontFamily = FontFamily.Serif)
                    Text("Monto original de las compras, distinto de las cuotas del mes", color = Color(0xFFB8CCC6), fontSize = 11.sp)
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

@Composable private fun SummaryHeroCard(available: Int, spent: Int, income: Int, reserved: Int, pendingCount: Int) {
    val today = Calendar.getInstance()
    val remainingDays = today.getActualMaximum(Calendar.DAY_OF_MONTH) - today.get(Calendar.DAY_OF_MONTH)
    GradientHero {
        Text("DISPONIBLE ESTE MES", color = Mint, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
        Text(if (income > 0) clp(available) else "Configura tu plan", modifier = Modifier.testTag("available-amount"), color = if (available < 0 && income > 0) Lavender else Paper,
            fontSize = if (income > 0) 40.sp else 29.sp, lineHeight = 48.sp, fontFamily = FontFamily.Serif)
        Text(if (income > 0) "Después de gastos, reservas pendientes, compromisos y ahorro" else "Agrega ingresos y compromisos en Planificación.", color = Mint, fontSize = 12.sp)
        if (pendingCount > 0) Text("Reserva pendiente del mes: ${clp(reserved)} · $pendingCount por revisar", color = Ochre, fontSize = 12.sp)
        HorizontalDivider(color = Mint.copy(alpha = .25f))
        Text(if (income > 0 && remainingDays > 0) "${clp(available.coerceAtLeast(0) / remainingDays)} por día · $remainingDays días restantes"
            else if (income > 0) "Cierre del mes · Gasto ${clp(spent)}" else "Tus gastos siguen registrándose con normalidad.", color = Paper, fontSize = 12.sp)
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
    Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
    Card(shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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

@Composable private fun SummaryMovementRow(event: CapturedEvent, onEdit: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onEdit).padding(vertical = 5.dp), verticalAlignment = Alignment.CenterVertically) {
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
            Text("${if (event.isPending) "Pendiente · " else ""}" + if (event.installmentCount > 1) "${event.categoryLabel()} · cuota de ${event.installmentCount} · compra ${clp(event.amount ?: 0)}" else "${event.categoryLabel()} · ${event.receivedAtShortLabel()}", color = Muted, fontSize = 12.sp)
        }
        val target = Calendar.getInstance()
        Text(clp(event.monthlyExpense(target.get(Calendar.YEAR), target.get(Calendar.MONTH))), color = Ink, fontWeight = FontWeight.Bold)
    }
}

@Composable private fun CapturedEventRow(event: CapturedEvent, onReview: () -> Unit = {}, onEdit: (() -> Unit)? = null, onIgnore: (() -> Unit)? = null) {
    Card(modifier = if (onEdit != null) Modifier.clickable(role = Role.Button, onClick = onEdit) else Modifier,
        shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
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
            if (event.installmentCount > 1) Text("${event.installmentCount} cuotas · primera ${event.firstInstallmentMonth ?: "por confirmar"}", color = Muted, fontSize = 12.sp)
            if (event.status == "requires_review") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onReview) { Text("Marcar como revisado", color = Green, fontSize = 12.sp) }
                }
            } else {
                Text(event.statusLabel(), color = Green, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
            if (onEdit != null && onIgnore != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onEdit) { Text("Editar movimiento") }
                TextButton(onClick = onIgnore) { Text("Ignorar") }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable private fun CapturedExpenseRow(event: CapturedEvent, onEditCategory: () -> Unit, onIgnore: () -> Unit, onConfirm: () -> Unit) {
    Card(modifier = Modifier.clickable(role = Role.Button, onClick = onEditCategory), shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("PENDIENTE DE CONFIRMACIÓN", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(event.merchant ?: "Comercio pendiente", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
            Text(clp(event.amount ?: 0), color = Green, fontFamily = FontFamily.Serif, fontSize = 23.sp)
            Text("${event.categoryLabel()} · ${event.movementTypeLabel()} · ${event.paymentSourceLabel()} · ${event.receivedAtShortLabel()}", color = Muted, fontSize = 12.sp)
            if (event.installmentCount > 1) Text("${event.installmentCount} cuotas · desde ${event.firstInstallmentMonth ?: "por confirmar"}", color = Muted, fontSize = 12.sp)
            FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (event.isPending) TextButton(onClick = onConfirm) { Text("Confirmar movimiento") }
                TextButton(onClick = onEditCategory) { Text("Editar") }
                TextButton(onClick = onIgnore) { Text("Ignorar") }
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
        val target = Calendar.getInstance()
        appendLine("- ${event.merchant ?: "Comercio sin nombre"} | ${event.categoryLabel()} | cargo mensual ${clp(event.monthlyExpense(target.get(Calendar.YEAR), target.get(Calendar.MONTH)))} | compra total ${clp(event.amount ?: 0)} | ${event.paymentSourceLabel()}")
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


private fun syncBundleJson(events: List<CapturedEvent>, planning: JSONObject? = null, store: CapturedEventStore? = null): String = JSONObject().apply {
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
                put("manually_edited", event.manuallyEdited)
                put("installment_count", event.installmentCount)
                put("first_installment_month", event.firstInstallmentMonth ?: JSONObject.NULL)
                put("payment_source", event.paymentSource)
                put("status", event.status)
                put("change_history", JSONArray().apply {
                    store?.history(event.id).orEmpty().forEach { change -> put(JSONObject().apply {
                        put("changed_at", change.changedAt); put("field", change.field)
                        put("old_value", change.oldValue ?: JSONObject.NULL); put("new_value", change.newValue ?: JSONObject.NULL)
                    }) }
                })
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
        if (status == "demo") return@count false
        val inserted = store.insertSyncEvent(
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
            categoryManual = event.optBoolean("category_manual", false),
            manuallyEdited = event.optBoolean("manually_edited", false),
            installmentCount = event.optInt("installment_count", 1),
            firstInstallmentMonth = if (event.isNull("first_installment_month")) null else event.optString("first_installment_month")
        )
        val history = event.optJSONArray("change_history")
        if (history != null) store.importHistory(fingerprint, (0 until history.length()).mapNotNull { changeIndex ->
            val change = history.optJSONObject(changeIndex) ?: return@mapNotNull null
            MovementChange(0, 0, change.optLong("changed_at"), change.optString("field"),
                if (change.isNull("old_value")) null else change.optString("old_value"),
                if (change.isNull("new_value")) null else change.optString("new_value"))
        })
        inserted
    }
    PlanningStore(activity).importJson(root.optJSONObject("planning") ?: JSONObject())
    return importedEvents
}
private fun capturedCsv(events: List<CapturedEvent>): String = buildString {
    appendLine("fecha_hora,estado,tipo_movimiento,categoria,medio_pago,comercio,monto,cuotas,primer_mes_cargo,paquete_origen,texto_original")
    events.forEach { event ->
        appendLine(listOf(
            event.receivedAtLabel(),
            event.statusLabel(),
            event.movementTypeLabel(),
            event.categoryLabel(),
            event.paymentSourceLabel(),
            event.merchant.orEmpty(),
            event.amount?.toString().orEmpty(),
            event.installmentCount.toString(),
            event.firstInstallmentMonth.orEmpty(),
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
