package dev.michelle.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.michelle.Goal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

private val dayMonth = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
private val month = DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)
private const val LEFT = 44f      // room for the y labels, in px at 1x (scaled by density below)
private const val BOTTOM = 22f

/** Where things go: days along x, values along y, inside the plot area. */
private class Frame(val from: LocalDate, val to: LocalDate, val lo: Double, val hi: Double, val left: Float, val top: Float, val w: Float, val h: Float) {
    private val days = max(1L, ChronoUnit.DAYS.between(from, to)).toFloat()
    fun x(d: LocalDate) = left + w * ChronoUnit.DAYS.between(from, d) / days
    fun y(v: Double) = top + h * (1 - ((v - lo) / (hi - lo)).toFloat())
    val dayWidth get() = w / (days + 1)
}

private fun DrawScope.axes(f: Frame, ticks: List<Double>, label: (Double) -> String, text: TextMeasurer, ink: Color, grid: Color, today: LocalDate) {
    val style = TextStyle(color = ink, fontSize = 11.sp)
    ticks.forEach { v ->
        drawLine(grid, Offset(f.left, f.y(v)), Offset(f.left + f.w, f.y(v)), 1f)
        val t = text.measure(label(v), style)
        drawText(t, topLeft = Offset(f.left - t.size.width - 6f, f.y(v) - t.size.height / 2))
    }
    // months along x: a faint line and the name at each month's first day
    val months = generateSequence(f.from.withDayOfMonth(1).plusMonths(if (f.from.dayOfMonth == 1) 0 else 1)) { it.plusMonths(1) }
        .takeWhile { !it.isAfter(f.to) }.toList()
    var lastEnd = Float.NEGATIVE_INFINITY
    // a short range may cross only one month start: label the first day too
    val labels = (if (months.size < 2) listOf(f.from to dayMonth.format(f.from)) else emptyList()) +
        months.map { it to month.format(it) }
    months.forEach { d -> drawLine(grid, Offset(f.x(d), f.top), Offset(f.x(d), f.top + f.h), 1f) }
    labels.sortedBy { it.first }.forEach { (d, s) ->
        val t = text.measure(s, style)
        val x = (f.x(d) + 4f).coerceAtMost(f.left + f.w - t.size.width)
        if (x > lastEnd + 8f) {
            drawText(t, topLeft = Offset(x, f.top + f.h + 4f))
            lastEnd = x + t.size.width
        }
    }
    if (!today.isBefore(f.from) && !today.isAfter(f.to)) {
        drawLine(ink.copy(alpha = 0.35f), Offset(f.x(today), f.top), Offset(f.x(today), f.top + f.h), 2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f)))
    }
}

private fun niceTicks(lo: Double, hi: Double, count: Int = 4): List<Double> {
    val raw = (hi - lo) / count
    val step = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0, 100.0, 200.0, 500.0, 1000.0).first { it >= raw }
    return generateSequence(ceil(lo / step) * step) { it + step }.takeWhile { it <= hi + 1e-9 }.toList()
}

/** Weights (dots), the smoothed trend (line), the plan (dashed line) and the target (ring) (R29). */
@Composable
fun WeightChart(from: LocalDate, to: LocalDate, today: LocalDate, weights: Map<LocalDate, Double>, trend: List<Pair<LocalDate, Double>>, goal: Goal?, modifier: Modifier = Modifier.fillMaxWidth().height(240.dp)) {
    val text = rememberTextMeasurer()
    val c = MaterialTheme.colorScheme
    val inRange = { d: LocalDate -> !d.isBefore(from) && !d.isAfter(to) }
    val ws = weights.filterKeys(inRange)
    val ts = trend.filter { inRange(it.first) }
    val planPts = goal?.let { g -> listOf(from, to).map { it to g.planKg(it) } }.orEmpty()
    val values = ws.values + ts.map { it.second } + planPts.map { it.second } + listOfNotNull(goal?.targetKg?.takeIf { inRange(goal.targetDate) })
    if (values.isEmpty()) return
    val lo = floor(values.min() - 0.5); val hi = ceil(values.max() + 0.5)
    Canvas(modifier) {
        val f = Frame(from, to, lo, hi, LEFT * density, 8f, size.width - LEFT * density - 8f, size.height - BOTTOM * density - 8f)
        axes(f, niceTicks(lo, hi), { "%.0f".format(it) }, text, c.onSurfaceVariant, c.outlineVariant, today)
        if (goal != null) {
            val a = if (goal.startDate.isAfter(from)) goal.startDate else from
            val b = if (goal.targetDate.isBefore(to)) goal.targetDate else to
            if (!b.isBefore(a)) drawLine(c.tertiary, Offset(f.x(a), f.y(goal.planKg(a))), Offset(f.x(b), f.y(goal.planKg(b))), 3f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
            if (inRange(goal.targetDate)) drawCircle(c.tertiary, 9f, Offset(f.x(goal.targetDate), f.y(goal.targetKg)), style = Stroke(3f))
        }
        if (ts.size > 1) drawPath(Path().apply {
            ts.forEachIndexed { i, (d, v) -> if (i == 0) moveTo(f.x(d), f.y(v)) else lineTo(f.x(d), f.y(v)) }
        }, c.primary, style = Stroke(5f))
        ws.forEach { (d, v) -> drawCircle(c.onSurface, 6f, Offset(f.x(d), f.y(v))) }
    }
}

/** Calories eaten per day (bars), the daily target (dashed line); days over it in the warning color (R30). */
@Composable
fun KcalChart(from: LocalDate, to: LocalDate, today: LocalDate, kcal: Map<LocalDate, Int>, target: Int?, modifier: Modifier = Modifier.fillMaxWidth().height(180.dp)) {
    val text = rememberTextMeasurer()
    val c = MaterialTheme.colorScheme
    val shown = kcal.filterKeys { !it.isBefore(from) && !it.isAfter(to) }
    val hi = max((shown.values.maxOrNull() ?: 0), target ?: 0).let { if (it == 0) 2000.0 else it * 1.15 }
    Canvas(modifier) {
        val f = Frame(from, to, 0.0, hi, LEFT * density, 8f, size.width - LEFT * density - 8f, size.height - BOTTOM * density - 8f)
        axes(f, niceTicks(0.0, hi, 3), { "%.0f".format(it) }, text, c.onSurfaceVariant, c.outlineVariant, today)
        val bar = (f.dayWidth * 0.7f).coerceAtLeast(2f)
        shown.forEach { (d, k) ->
            val over = target != null && k > target
            val color = if (over) c.error else c.primary
            drawRect(color.copy(alpha = if (d == today) 0.6f else 1f), Offset(f.x(d) - bar / 2, f.y(k.toDouble())), Size(bar, f.y(0.0) - f.y(k.toDouble())))
        }
        if (target != null) drawLine(c.tertiary, Offset(f.left, f.y(target.toDouble())), Offset(f.left + f.w, f.y(target.toDouble())), 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
    }
}
