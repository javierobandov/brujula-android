package cl.brujula.app

import android.os.Bundle
import android.content.Intent
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
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        activity.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { writer ->
            writer.write(capturedCsv(captured))
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
    val realCaptured = captured.filter { it.status == "captured" && it.amount != null }
    val expenses = realCaptured.filter { it.movementType == "expense" }
    val total = expenses.filter { it.receivedAt.isCurrentMonth() }.sumOf { it.amount ?: 0 }
    MaterialTheme(colorScheme = lightColorScheme(primary = Green, background = Paper)) {
        Surface(color = Paper, modifier = Modifier.fillMaxSize()) {
            LazyColumn(contentPadding = PaddingValues(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                item { Text("BRÚJULA", color = Green, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp); Text("Tus gastos claros.", color = Ink, fontSize = 31.sp, fontWeight = FontWeight.Bold); Text("Captura local en prueba · sin sincronización bancaria", color = Color.Gray, fontSize = 14.sp) }
                item {
                    Card(shape = RoundedCornerShape(18.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(20.dp)) { Text("Gasto del mes", color = Color.Gray, fontSize = 13.sp); Text(clp(total), color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(14.dp)); LinearProgressIndicator(progress = { (total / 1_000_000f).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = Green, trackColor = Color(0xFFE8EEE9)); Spacer(Modifier.height(8.dp)); Text("${percent(total)}% del presupuesto utilizado", color = Green, fontSize = 12.sp) }
                    }
                }
                item {
                    Button(onClick = { syncing = true; captured = CapturedEventStore(activity).recent(); syncing = false }, modifier = Modifier.fillMaxWidth(), enabled = !syncing) { Text(if (syncing) "Actualizando…" else "Actualizar resumen") }
                    OutlinedButton(onClick = { exportLauncher.launch("brujula-eventos-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.csv") }, modifier = Modifier.fillMaxWidth()) {
                        Text("Exportar bandeja CSV")
                    }
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
                    items(captured) { event -> CapturedEventRow(event) }
                }
                item { Text("Movimientos reales capturados", color = Ink, fontSize = 21.sp, fontWeight = FontWeight.Bold) }
                if (realCaptured.isEmpty()) {
                    item { Text("Aún no hay movimientos reales. Los eventos DEMO no se incluyen en el resumen.", color = Color.Gray, fontSize = 13.sp) }
                } else {
                    items(realCaptured) { event -> CapturedExpenseRow(event) }
                }
            }
        }
    }
}

@Composable private fun CapturedEventRow(event: CapturedEvent) {
    Card(shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6F1))) {
        Column(Modifier.fillMaxWidth().padding(15.dp)) {
            Text(event.merchant ?: "Notificación sin comercio", color = Ink, fontWeight = FontWeight.SemiBold)
            Text(event.rawText, color = Color.Gray, fontSize = 12.sp, maxLines = 3)
            Text("${event.statusLabel()} · ${event.receivedAtLabel()}${event.amount?.let { " · ${clp(it)}" } ?: ""}", color = Green, fontSize = 12.sp)
        }
    }
}

@Composable private fun CapturedExpenseRow(event: CapturedEvent) {
    Card(shape = RoundedCornerShape(13.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) { Text(event.merchant ?: "Comercio pendiente", color = Ink, fontWeight = FontWeight.SemiBold); Text("${event.movementTypeLabel()} · ${event.receivedAtShortLabel()}", color = Color.Gray, fontSize = 12.sp) }
            Text(clp(event.amount ?: 0), color = Ink, fontWeight = FontWeight.Bold)
        }
    }
}

private fun clp(amount: Int) = NumberFormat.getCurrencyInstance(Locale("es", "CL")).apply { maximumFractionDigits = 0 }.format(amount)
private fun percent(amount: Int) = (amount / 1_000_000f * 100).toInt()
private fun CapturedEvent.statusLabel() = if (status == "demo") "DEMO" else "CAPTURADO"
private fun CapturedEvent.receivedAtLabel() = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("es", "CL")).format(Date(receivedAt))
private fun CapturedEvent.receivedAtShortLabel() = SimpleDateFormat("dd MMM, HH:mm", Locale("es", "CL")).format(Date(receivedAt))
private fun Long.isCurrentMonth(): Boolean {
    val event = Calendar.getInstance().apply { timeInMillis = this@isCurrentMonth }
    val now = Calendar.getInstance()
    return event.get(Calendar.YEAR) == now.get(Calendar.YEAR) && event.get(Calendar.MONTH) == now.get(Calendar.MONTH)
}

private fun capturedCsv(events: List<CapturedEvent>): String = buildString {
    appendLine("fecha_hora,estado,tipo_movimiento,comercio,monto,paquete_origen,texto_original")
    events.forEach { event ->
        appendLine(listOf(
            event.receivedAtLabel(),
            event.statusLabel(),
            event.movementTypeLabel(),
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

private fun csvCell(value: String): String = "\"${value.replace("\"", "\"\"")}\""
