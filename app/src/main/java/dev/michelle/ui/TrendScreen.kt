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
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
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
fun TrendScreen(onBack: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    var progress by remember { mutableStateOf<Progress?>(null) }
    var loads by remember { mutableIntStateOf(0) }
    var plan by remember { mutableStateOf(true) }
    var editGoal by remember { mutableStateOf(false) }
    LaunchedEffect(loads) { progress = Progress.load(app) }
    val ask = rememberLauncherForActivityResult(app.health.permissionContract) { loads++ }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Plan") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            // the goal editor opens in place of the graphs (R22); without a goal it's open already
            actions = {
                val g = progress?.goal
                if (g != null) TextButton(onClick = { editGoal = !editGoal }) { Text(if (editGoal) "Close" else "Change") }
            },
        )
    }) { pad ->
        val p = progress
        Column(
            Modifier.fillMaxSize().padding(pad)
                .then(if (editGoal || progress?.goal == null) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (p == null) { CircularProgressIndicator(); return@Column }
            val g = p.goal
            if (!p.canReadWeight && app.health.available) Row(verticalAlignment = Alignment.CenterVertically) {
                Caption("Can't read your weight yet.", Modifier.weight(1f))
                TextButton(onClick = { ask.launch(BodyReader.PERMISSIONS) }) { Text("Allow") }
            }
            if (g != null && !editGoal) {  // range and numbers
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(plan, { plan = true }, SegmentedButtonDefaults.itemShape(0, 2)) { Text("Whole plan") }
                    SegmentedButton(!plan, { plan = false }, SegmentedButtonDefaults.itemShape(1, 2)) { Text("30 days") }
                }
                Numbers(p)
            }
            val editing = editGoal || g == null
            if (!editing) {
                val (from, to) = if (g != null && plan) g.startDate to g.targetDate else p.today.minusDays(29) to p.today
                Caption("Weight, kg")
                if (p.weights.isEmpty()) Caption("No weights yet.")
                else WeightChart(from, to, p.today, p.weights, p.trend, g, Modifier.fillMaxWidth().weight(1f))
                Caption("Calories a day" + (p.kcalTarget?.let { ", target $it" } ?: ""))
                KcalChart(from, if (to.isAfter(p.today) && !plan) p.today else to, p.today, p.kcal, p.kcalTarget, Modifier.fillMaxWidth().weight(1f))
            }
            if (editing) {
                if (g == null) Caption("Set your goal to see the plan.")
                GoalSection(onSaved = { editGoal = false; loads++ })
            }
        }
    }
}

@Composable
private fun Caption(s: String, modifier: Modifier = Modifier) =
    Text(s, modifier, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)

/** Latest weight, the plan for that day, the difference: one row (R31). */
@Composable
private fun Numbers(p: Progress) {
    val g = p.goal ?: return
    val (day, w) = p.latest ?: return
    val plan = g.planKg(day)
    val diff = w - plan
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Stat(kg(w), if (day == p.today) "today" else dm.format(day))
        Stat(kg(plan), "plan")
        Stat((if (diff >= 0) "+" else "−") + kg(kotlin.math.abs(diff)), if (diff > 0) "above plan" else "below plan",
            if (diff > 0.05) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
    }
}

@Composable
private fun Stat(value: String, label: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleLarge, color = color)
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private val dm = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)
