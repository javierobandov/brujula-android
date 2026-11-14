package cl.brujula.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

internal val Green = Color(0xFF173F38)
internal val Paper = Color(0xFFF7F3E9)
internal val Ink = Color(0xFF172F29)
internal val Mint = Color(0xFFDAEADF)
internal val Lavender = Color(0xFFF2D6C7)
internal val Muted = Color(0xFF65776D)
internal val Coral = Color(0xFFEF8565)
internal val Ochre = Color(0xFFE9C96D)
internal val Line = Color(0xFFE5E7DF)
private val HeroEnd = Color(0xFF397963)

private fun money(value: Int) = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-CL")).format(value)

/** Pure decoration: it is deliberately absent from accessibility semantics. */
@Composable internal fun CompassMark(modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val s = size.minDimension
        val center = Offset(size.width / 2, size.height / 2)
        drawCircle(Mint.copy(alpha = .24f), s * .44f, center, style = Stroke(s * .007f))
        drawCircle(Mint.copy(alpha = .2f), s * .31f, center, style = Stroke(s * .007f))
        val needle = Path().apply {
            moveTo(center.x + s * .18f, center.y - s * .22f)
            lineTo(center.x + s * .06f, center.y + s * .07f)
            lineTo(center.x - s * .18f, center.y + s * .22f)
            lineTo(center.x - s * .07f, center.y - s * .07f)
            close()
        }
        drawPath(needle, Mint.copy(alpha = .24f))
        val north = Path().apply {
            moveTo(center.x + s * .18f, center.y - s * .22f)
            lineTo(center.x + s * .06f, center.y + s * .07f)
            lineTo(center.x - s * .07f, center.y - s * .07f)
            close()
        }
        drawPath(north, Ochre.copy(alpha = .8f))
        drawCircle(Paper, s * .026f, center)
    }
}

@Composable internal fun BrandHeader(onAccount: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(Green).padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        CompassMark(Modifier.size(38.dp))
        Text("BRÚJULA", color = Paper, fontSize = 17.sp, fontWeight = FontWeight.Bold,
            letterSpacing = 2.5.sp, modifier = Modifier.weight(1f))
        FilledIconButton(onClick = onAccount, colors = IconButtonDefaults.filledIconButtonColors(containerColor = Lavender, contentColor = Green),
            modifier = Modifier.size(48.dp)) {
            Icon(Icons.Filled.Person, contentDescription = "Abrir cuenta")
        }
    }
}

@Composable internal fun EditorialHeading(eyebrow: String, title: String, subtitle: String? = null) {
    Column(Modifier.fillMaxWidth().padding(bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(eyebrow.uppercase(Locale.forLanguageTag("es-CL")), color = Muted, fontSize = 10.sp,
            letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
        Text(title, color = Ink, fontSize = 32.sp, lineHeight = 40.sp, fontFamily = FontFamily.Serif)
        subtitle?.let { Text(it, color = Muted, fontSize = 13.sp) }
    }
}

@Composable internal fun GradientHero(content: @Composable ColumnScope.() -> Unit) {
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(28.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 7.dp),
        colors = CardDefaults.cardColors(containerColor = Green)) {
        Box(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Green, HeroEnd)))) {
            CompassMark(Modifier.align(Alignment.TopEnd).offset(x = 32.dp, y = (-24).dp).size(148.dp))
            Column(Modifier.fillMaxWidth().padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
        }
    }
}

@Composable internal fun MonthMetrics(spent: Int, goal: Int) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        listOf(Triple("Gasto del mes", spent, Lavender), Triple("Meta de ahorro", goal, Mint)).forEach { (label, value, color) ->
            Card(Modifier.weight(1f), shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), colors = CardDefaults.cardColors(containerColor = color)) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(label, color = Muted, fontSize = 11.sp)
                    Text(money(value), color = Ink, fontSize = 23.sp, fontFamily = FontFamily.Serif)
                }
            }
        }
    }
}

@Composable internal fun BudgetDistribution(spent: Int, goal: Int, available: Int) {
    val parts = listOf(spent.coerceAtLeast(0), goal.coerceAtLeast(0), available.coerceAtLeast(0))
    val total = parts.sumOf { it.toLong() }
    if (total == 0L) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("ASÍ SE DISTRIBUYE TU PLAN", color = Muted, fontSize = 10.sp, letterSpacing = .6.sp, fontWeight = FontWeight.Bold)
        Row(Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            parts.forEachIndexed { index, value ->
                if (value > 0) Box(Modifier.weight(value.toFloat() / total).fillMaxHeight()
                    .background(listOf(Coral, Ochre, Green)[index]))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Gasto", color = Muted, fontSize = 11.sp)
            Text("Ahorro", color = Muted, fontSize = 11.sp)
            Text(if (available < 0) "Plan excedido" else "Disponible", color = Green, fontSize = 11.sp)
        }
    }
}

/** Same installmentChargeForMonth contract as the ledger; no duplicated purchase events. */
@Composable internal fun InstallmentOverview(expenses: List<CapturedEvent>) {
    val now = Calendar.getInstance()
    val purchases = expenses.filter { it.installmentCount > 1 }
    if (purchases.isEmpty()) return
    val months = (0..5).map { offset ->
        (now.clone() as Calendar).apply { add(Calendar.MONTH, offset) }
    }
    val values = months.map { date -> purchases.sumOf { it.monthlyExpense(date.get(Calendar.YEAR), date.get(Calendar.MONTH)) } }
    GradientHero {
        Text("CUOTAS DE ESTE MES", color = Mint, fontSize = 10.sp, letterSpacing = 1.2.sp, fontWeight = FontWeight.Bold)
        Text(money(values.first()), color = Paper, fontFamily = FontFamily.Serif, fontSize = 36.sp)
        Text("Incluidas en el gasto del mes; no se suman otra vez.", color = Mint, fontSize = 12.sp)
    }
    Spacer(Modifier.height(16.dp))
    Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text("Tus próximos pagos", color = Ink, fontFamily = FontFamily.Serif, fontSize = 22.sp)
            Row(Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Bottom) {
                val max = (values.maxOrNull() ?: 0).coerceAtLeast(1)
                months.forEachIndexed { index, month ->
                    Column(Modifier.width(56.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(money(values[index]), color = Muted, fontSize = 10.sp)
                        Box(Modifier.height(88.dp).fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
                            Box(Modifier.width(30.dp).height((values[index].toFloat() / max * 80).coerceAtLeast(2f).dp)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(if (index == 0) Brush.verticalGradient(listOf(Coral, Lavender)) else Brush.verticalGradient(listOf(HeroEnd, Mint))))
                        }
                        Text(SimpleDateFormat("MMM yy", Locale.forLanguageTag("es-CL")).format(month.time), color = Muted, fontSize = 11.sp)
                    }
                }
            }
            Text("Cuotas mensuales en CLP · El total comprado se muestra por separado.", color = Muted, fontSize = 11.sp)
        }
    }
    Spacer(Modifier.height(16.dp))
}

@Composable internal fun InstallmentPurchaseRow(event: CapturedEvent, monthly: Int, onEdit: () -> Unit) {
    Card(Modifier.fillMaxWidth().padding(vertical = 5.dp).clickable(role = Role.Button, onClick = onEdit),
        shape = RoundedCornerShape(24.dp), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(event.merchant ?: "Compra en cuotas", color = Ink, fontWeight = FontWeight.SemiBold)
            Text("${money(monthly)} / mes", color = Green, fontSize = 20.sp, fontFamily = FontFamily.Serif)
            Text("${event.installmentCount} cuotas · Desde ${event.firstInstallmentMonth ?: "fecha de compra"}", color = Muted, fontSize = 12.sp)
            Text("Compra total: ${money(event.amount ?: 0)} · Toca para editar", color = Muted, fontSize = 11.sp)
        }
    }
}
