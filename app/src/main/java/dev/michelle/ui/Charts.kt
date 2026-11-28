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
import dev.michelle.Prediction
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
    fun x(d: LocalDate) = xAt(ChronoUnit.DAYS.between(from, d).toDouble())
    fun xAt(day: Double) = left + w * (day / days).toFloat()
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

/** Weights (dots), the plan (dashed line), the target (ring) and the [prediction] in blue (R29). */
@Composable
fun WeightChart(from: LocalDate, to: LocalDate, today: LocalDate, weights: Map<LocalDate, Double>, goal: Goal?, prediction: Prediction, modifier: Modifier = Modifier.fillMaxWidth().height(240.dp)) {
    val text = rememberTextMeasurer()
    val c = MaterialTheme.colorScheme
    val inRange = { d: LocalDate -> !d.isBefore(from) && !d.isAfter(to) }
    val ws = weights.filterKeys(inRange)
    val planPts = goal?.let { g -> listOf(from, to).map { it to g.planKg(it) } }.orEmpty()
    val values = ws.values + planPts.map { it.second } + listOfNotNull(goal?.targetKg?.takeIf { inRange(goal.targetDate) })
    if (values.isEmpty()) return
    val lo = floor(values.min() - 0.5); val hi = ceil(values.max() + 0.5)
    val line = predict(prediction, days(from, to, weights))
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
        line?.let { draw(f, it) }
        ws.forEach { (d, v) -> drawCircle(c.onSurface, 6f, Offset(f.x(d), f.y(v))) }
    }
}

/**
 * Calories eaten per day (dots), days over the target in the warning color, the daily target (dashed line) and the
 * average of the days shown (blue line) (R30). Today is still being eaten, so it stays out of the average.
 */
@Composable
fun KcalChart(from: LocalDate, to: LocalDate, today: LocalDate, kcal: Map<LocalDate, Int>, target: Int?, modifier: Modifier = Modifier.fillMaxWidth().height(180.dp)) {
    val text = rememberTextMeasurer()
    val c = MaterialTheme.colorScheme
    val shown = kcal.filterKeys { !it.isBefore(from) && !it.isAfter(to) }
    val average = shown.filterKeys { it != today }.values.takeIf { it.isNotEmpty() }?.average()
    val hi = max((shown.values.maxOrNull() ?: 0), target ?: 0).let { if (it == 0) 2000.0 else it * 1.15 }
    Canvas(modifier) {
        val f = Frame(from, to, 0.0, hi, LEFT * density, 8f, size.width - LEFT * density - 8f, size.height - BOTTOM * density - 8f)
        axes(f, niceTicks(0.0, hi, 3), { "%.0f".format(it) }, text, c.onSurfaceVariant, c.outlineVariant, today)
        if (target != null) drawLine(c.tertiary, Offset(f.left, f.y(target.toDouble())), Offset(f.left + f.w, f.y(target.toDouble())), 3f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        if (average != null) drawLine(FitBlue, Offset(f.left, f.y(average)), Offset(f.left + f.w, f.y(average)), 4f)
        shown.forEach { (d, k) ->
            val over = target != null && k > target
            val color = if (over) c.error else c.primary
            drawCircle(color.copy(alpha = if (d == today) 0.6f else 1f), 6f, Offset(f.x(d), f.y(k.toDouble())))
        }
    }
}

/** Every value as (days since [from], value), up to [to]: the ones before [from] too, for the 7-day average. */
private fun days(from: LocalDate, to: LocalDate, values: Map<LocalDate, Double>) =
    values.filterKeys { !it.isAfter(to) }.toSortedMap().map { (d, v) -> ChronoUnit.DAYS.between(from, d).toDouble() to v }

/** What to draw for a prediction: a straight line a·day + b from day x0 on, or a path then held flat. */
private sealed interface Line {
    data class Straight(val a: Double, val b: Double, val x0: Double) : Line
    data class Flat(val path: List<Pair<Double, Double>>) : Line
}

/** The prediction through [pts] (days since the graph's first day, value); only days on the graph are fitted. */
private fun predict(kind: Prediction, pts: List<Pair<Double, Double>>): Line? {
    val shown = pts.filter { it.first >= 0 }
    val straight = { fit: Pair<Double, Double>? -> fit?.let { (a, b) -> Line.Straight(a, b, shown.minOf { it.first }) } }
    return when (kind) {
        Prediction.THEIL_SEN -> straight(theilSen(shown))
        Prediction.LEAST_SQUARES -> straight(leastSquares(shown))
        Prediction.WEEK_AVERAGE -> weekAverage(pts).filter { it.first >= 0 }.takeIf { it.isNotEmpty() }?.let { Line.Flat(it) }
        Prediction.NONE -> null
    }
}

private val FitBlue = Color(0xFF64B5F6)

/** The prediction in blue, on to the end of the graph: where things are heading. A straight line stops at the plot's edge. */
private fun DrawScope.draw(f: Frame, line: Line) {
    val end = ChronoUnit.DAYS.between(f.from, f.to).toDouble()
    when (line) {
        is Line.Straight -> {
            val (a, b, x0) = line
            var x1 = end
            val y1 = a * x1 + b
            if (a != 0.0 && (y1 > f.hi || y1 < f.lo)) x1 = ((if (y1 > f.hi) f.hi else f.lo) - b) / a
            if (x1 > x0) drawLine(FitBlue, Offset(f.xAt(x0), f.y(a * x0 + b)), Offset(f.xAt(x1), f.y(a * x1 + b)), 4f)
        }
        is Line.Flat -> {
            if (line.path.size > 1) drawPath(Path().apply {
                line.path.forEachIndexed { i, (x, y) -> if (i == 0) moveTo(f.xAt(x), f.y(y)) else lineTo(f.xAt(x), f.y(y)) }
            }, FitBlue, style = Stroke(4f))
            val (x, y) = line.path.last()
            if (x < end) drawLine(FitBlue, Offset(f.xAt(x), f.y(y)), Offset(f.xAt(end), f.y(y)), 4f,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(14f, 10f)))
        }
    }
}

/**
 * Theil–Sen line y = a·x + b: a is the median of the slopes between every two points, b the median of y − a·x,
 * so a single odd day barely moves it. Null with fewer than two different x.
 */
internal fun theilSen(pts: List<Pair<Double, Double>>): Pair<Double, Double>? {
    val slopes = pts.indices.flatMap { i -> (i + 1 until pts.size).mapNotNull { j ->
        val (x1, y1) = pts[i]; val (x2, y2) = pts[j]
        if (x1 == x2) null else (y2 - y1) / (x2 - x1)
    } }
    if (slopes.isEmpty()) return null
    val a = median(slopes)
    return a to median(pts.map { (x, y) -> y - a * x })
}

/** Least squares y = a·x + b; null with fewer than two different x. */
internal fun leastSquares(pts: List<Pair<Double, Double>>): Pair<Double, Double>? {
    if (pts.map { it.first }.distinct().size < 2) return null
    val mx = pts.sumOf { it.first } / pts.size
    val my = pts.sumOf { it.second } / pts.size
    val a = pts.sumOf { (x, y) -> (x - mx) * (y - my) } / pts.sumOf { (x, _) -> (x - mx) * (x - mx) }
    return a to my - a * mx
}

/** For each point, the mean of the points in the 7 days ending on it; days with nothing don't count as zero. */
internal fun weekAverage(pts: List<Pair<Double, Double>>): List<Pair<Double, Double>> =
    pts.map { (x, _) -> x to pts.filter { it.first <= x && it.first > x - 7 }.map { it.second }.average() }

private fun median(v: List<Double>): Double = v.sorted().let { s ->
    if (s.size % 2 == 1) s[s.size / 2] else (s[s.size / 2 - 1] + s[s.size / 2]) / 2
}
