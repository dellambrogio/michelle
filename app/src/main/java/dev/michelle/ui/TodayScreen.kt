package dev.michelle.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.TopAppBar
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
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(onCamera: () -> Unit, onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    // today changes at midnight, and the key may have been set in settings: look again on each resume
    var day by remember { mutableStateOf(LocalDate.now()) }
    var hasKey by remember { mutableStateOf(app.secrets.apiKey != null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            day = LocalDate.now()
            hasKey = app.secrets.apiKey != null
        }
    }
    val meals by remember(day) { app.meals.on(day) }.collectAsState(emptyList())
    val totals = Totals.ofMeals(meals)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(painterResource(R.drawable.whale), null, Modifier.size(40.dp))
                        Spacer(Modifier.width(10.dp))
                        Text("Today")
                    }
                },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Default.Settings, "Settings") } },
            )
        },
        floatingActionButton = {
            if (hasKey) ExtendedFloatingActionButton(
                onClick = onCamera,
                icon = { Icon(painterResource(R.drawable.ic_camera), null) },
                text = { Text("Photo") },
            )
        },
    ) { pad ->
        LazyColumn(
            Modifier.fillMaxSize().padding(pad),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item { TotalsCard(totals) }
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
                Text("No meals yet today. Take a photo of your plate.", Modifier.padding(8.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(meals, key = { it.meal.id }) { MealRow(it, health = app.health.available) { onOpen(it.meal.id) } }
        }
    }
}

@Composable
private fun TotalsCard(t: Totals) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp)) {
            Text("${t.kcal} kcal", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
            Text(macros(t), style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MealRow(m: MealWithItems, health: Boolean, onClick: () -> Unit) {
    Card(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Photo(m.meal.photoPath, 160, Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)))
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
