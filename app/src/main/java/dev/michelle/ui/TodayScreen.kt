package dev.michelle.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import dev.michelle.R
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import dev.michelle.App
import dev.michelle.data.MealType
import dev.michelle.data.MealWithItems
import dev.michelle.data.Totals
import dev.michelle.trend.Progress
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(onCamera: () -> Unit, onDescribe: () -> Unit, onOpen: (Long) -> Unit, onSettings: () -> Unit, onTrend: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    // today changes at midnight, and the key may have been set in settings: look again on each resume
    var day by remember { mutableStateOf(LocalDate.now()) }
    var onToday by remember { mutableStateOf(true) }      // follows the date (past midnight) until another day is picked
    var hasKey by remember { mutableStateOf(app.secrets.apiKey != null) }
    var progress by remember { mutableStateOf<Progress?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            if (onToday) day = LocalDate.now()
            hasKey = app.secrets.apiKey != null
            progress = Progress.load(app, from = LocalDate.now().minusDays(90))
        }
    }
    val meals by remember(day) { app.meals.on(day) }.collectAsState(emptyList())
    val totals = Totals.ofMeals(meals)

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = { Image(painterResource(R.drawable.whale), null, Modifier.padding(start = 12.dp).size(34.dp)) },
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { day = day.minusDays(1); onToday = false }) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, "Day before")
                        }
                        Column(
                            Modifier.clickable(enabled = day != LocalDate.now()) { day = LocalDate.now(); onToday = true },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(dayName(day), style = MaterialTheme.typography.titleLarge)
                            Text(dayDate.format(day), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = {
                            day = day.plusDays(1)
                            onToday = day == LocalDate.now()
                        }, enabled = day.isBefore(LocalDate.now())) {
                            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, "Day after")
                        }
                    }
                },
                actions = {
                    IconButton(onClick = onTrend) { Icon(painterResource(R.drawable.ic_chart), "Trend") }
                    IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") }
                },
            )
        },
        floatingActionButton = {
            if (hasKey) Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ExtendedFloatingActionButton(
                    onClick = onDescribe,
                    icon = { Icon(painterResource(R.drawable.ic_edit), null) },
                    text = { Text("Manual") },
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                )
                ExtendedFloatingActionButton(
                    onClick = onCamera,
                    icon = { Icon(painterResource(R.drawable.ic_camera), null) },
                    text = { Text("Photo") },
                )
            }
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { TotalsCard(totals, progress?.kcalTarget) }
            if (!hasKey) item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Michelle needs an Anthropic API key to look at your photos.")
                        Spacer(Modifier.size(8.dp))
                        Button(onClick = onSettings) { Text("Add the key") }
                    }
                }
            }
            if (meals.isEmpty() && hasKey) item {
                Text(if (day == LocalDate.now()) "No meals yet today. Take a photo of your plate, or add it manually." else "No meals on this day.", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(meals, key = { it.meal.id }) { MealRow(it, health = app.health.available) { onOpen(it.meal.id) } }
        }
    }
}

/** The day's kcal against the target, as a bar: red when over (R27); and the macros. */
@Composable
private fun TotalsCard(t: Totals, target: Int?) {
    val c = MaterialTheme.colorScheme
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${t.kcal}", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
                Text(if (target != null && target > 0) " / $target kcal" else " kcal", Modifier.padding(start = 4.dp, bottom = 6.dp),
                    style = MaterialTheme.typography.titleMedium, color = c.onSurfaceVariant)
            }
            if (target != null && target > 0) {
                val over = t.kcal > target
                LinearProgressIndicator(
                    progress = { (t.kcal.toFloat() / target).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(12.dp).clip(RoundedCornerShape(6.dp)),
                    color = if (over) c.error else c.primary,
                    trackColor = c.surfaceContainerLowest,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(
                    if (over) "${t.kcal - target} kcal over" else "${target - t.kcal} kcal left",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (over) c.error else c.primary,
                )
            }
            Text(macros(t), style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
        }
    }
}

private val dayDate = java.time.format.DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale.ENGLISH)

private fun dayName(d: LocalDate) = when (d) {
    LocalDate.now() -> "Today"
    LocalDate.now().minusDays(1) -> "Yesterday"
    else -> java.time.format.DateTimeFormatter.ofPattern("EEEE", java.util.Locale.ENGLISH).format(d)
}

private val dayLabel = java.time.format.DateTimeFormatter.ofPattern("d MMM", java.util.Locale.ENGLISH)

@Composable
private fun MealRow(m: MealWithItems, health: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (m.meal.photoPath != null) Photo(m.meal.photoPath, 160, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
            else Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)).background(MaterialTheme.colorScheme.surfaceContainerLowest), contentAlignment = Alignment.Center) {
                Icon(painterResource(R.drawable.ic_edit), "Entered manually", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(m.meal.title.ifBlank { "Meal" }, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${clock(m.meal.time)} · ${MealType.of(m.meal.mealType).label}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (health && !m.meal.hcSynced) {
                        Spacer(Modifier.width(6.dp))
                        Icon(painterResource(R.drawable.ic_cloud_off), "Not in Health Connect", Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            Text("${m.totals.kcal} kcal", style = MaterialTheme.typography.titleMedium)
        }
    }
}
