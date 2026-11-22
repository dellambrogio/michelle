package dev.michelle.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import dev.michelle.App
import dev.michelle.health.BodyReader
import dev.michelle.trend.Progress
import java.time.temporal.ChronoUnit

/** Weight against the plan, calories against the target (R29–R31). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrendScreen(onBack: () -> Unit, onSettings: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    var progress by remember { mutableStateOf<Progress?>(null) }
    var loads by remember { mutableIntStateOf(0) }
    var plan by remember { mutableStateOf(true) }
    LaunchedEffect(loads) { progress = Progress.load(app) }
    val ask = rememberLauncherForActivityResult(app.health.permissionContract) { loads++ }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Trend") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { pad ->
        val p = progress
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (p == null) { CircularProgressIndicator(); return@Column }
            val g = p.goal
            if (g == null) {
                Text("Set a goal (target weight and date) and your profile to see the plan and the calories.")
                Button(onClick = onSettings) { Text("Set a goal") }
            }
            if (!p.canReadWeight && app.health.available) {
                Text("Michelle can't read your weight from Health Connect yet.")
                Button(onClick = { ask.launch(BodyReader.PERMISSIONS) }) { Text("Allow") }
            }
            if (g != null) SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(plan, { plan = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Plan") }
                SegmentedButton(!plan, { plan = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("30 days") }
            }
            val (from, to) = if (g != null && plan) g.startDate to g.targetDate else p.today.minusDays(29) to p.today

            Summary(p)
            Text("Weight (kg)", style = MaterialTheme.typography.titleMedium)
            if (p.weights.isEmpty()) Text("No weights yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            else {
                WeightChart(from, to, p.today, p.weights, p.trend, g)
                Legend("● weighed   — trend" + if (g != null) "   - - plan   ○ target" else "")
            }
            Text("Calories eaten (kcal)", style = MaterialTheme.typography.titleMedium)
            KcalChart(from, if (to.isAfter(p.today) && !plan) p.today else to, p.today, p.kcal, p.kcalTarget)
            Legend("▮ a day of Michelle's meals" + (p.kcalTarget?.let { "   - - target $it today" } ?: ""))
        }
    }
}

@Composable
private fun Legend(s: String) = Text(s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** The numbers in words (R31): the latest weighing against the plan on that day. */
@Composable
private fun Summary(p: Progress) {
    val g = p.goal
    val latest = p.latest
    val week = (1..7L).map { p.today.minusDays(it) }.mapNotNull { p.kcal[it] }
    val dim = MaterialTheme.colorScheme.onSurfaceVariant
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (latest != null) Row {
                Text("Latest weight  ", color = dim)
                Text("%.1f kg".format(latest.second) + if (latest.first == p.today) " today" else " on " + dm.format(latest.first))
            }
            if (g != null && latest != null) {
                val plan = g.planKg(latest.first)
                val diff = latest.second - plan
                Row { Text("Plan for that day  ", color = dim); Text("%.1f kg".format(plan)) }
                Row {
                    Text("Difference  ", color = dim)
                    Text("%+.1f kg %s".format(diff, if (diff > 0) "above plan" else "below plan"),
                        color = if (diff > 0.05) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                }
                Row { Text("To go  ", color = dim); Text("%.1f kg in %d days".format(latest.second - g.targetKg, ChronoUnit.DAYS.between(p.today, g.targetDate).coerceAtLeast(0))) }
            }
            p.trendKg?.let { Row { Text("Trend (smoothed)  ", color = dim); Text("%.1f kg".format(it)) } }
            if (week.isNotEmpty()) Row {
                Text("Last 7 days  ", color = dim)
                Text("${week.average().toInt()} kcal a day" + (p.kcalTarget?.let { " (target $it)" } ?: "") + if (week.size < 7) ", ${week.size} days logged" else "")
            }
        }
    }
}

private val dm = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)
