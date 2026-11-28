package dev.michelle.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.michelle.App
import dev.michelle.ai.ReviewOutcome
import dev.michelle.ai.ReviewPrompt
import dev.michelle.ai.Trouble
import dev.michelle.trend.Progress
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Claude's look back at the last two weeks of meals (R35). The last review is kept and shown at once; a new one
 * is written on Refresh, or by itself when there's none yet or the last is a week old.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(onBack: () -> Unit, onSettings: () -> Unit) {
    val app = LocalContext.current.applicationContext as App
    var saved by remember { mutableStateOf(app.reviews.saved) }
    var writing by remember { mutableStateOf(false) }
    var trouble by remember { mutableStateOf<String?>(null) }
    var asks by remember { mutableIntStateOf(0) }
    val hasKey = app.secrets.apiKey != null
    val stale = saved.let { it == null || System.currentTimeMillis() - it.writtenAt > 7 * DAY_MS }

    LaunchedEffect(asks) {
        if (asks == 0 && !(stale && hasKey)) return@LaunchedEffect
        writing = true
        trouble = null
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val to = today.minusDays(1)                       // whole days only: today isn't over
        val from = to.minusDays(ReviewPrompt.DAYS - 1)
        val meals = app.meals.range(from, to).first()
        if (meals.isEmpty()) {
            trouble = "No meals logged from ${dm.format(from)} to ${dm.format(to)}: nothing to review yet."
        } else {
            val p = Progress.load(app, from = from)
            val log = ReviewPrompt.log(today, from, to, zone, p.profile, p.goal, p.kcalTarget, p.weights, meals)
            when (val r = app.analyzer.review(log)) {
                is ReviewOutcome.Done -> {
                    app.reviews.save(r.raw, System.currentTimeMillis(), from, to, meals.size, r.model)
                    saved = app.reviews.saved
                }
                is ReviewOutcome.Failed -> trouble =
                    if (r.trouble == Trouble.REFUSED) "Claude declined to write the review." else r.trouble.message
            }
        }
        writing = false
    }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Review") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            actions = {
                if (hasKey) IconButton(onClick = { asks++ }, enabled = !writing) {
                    if (writing) CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    else Icon(Icons.Default.Refresh, "Write a new review")
                }
            },
        )
    }) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!hasKey) Row(verticalAlignment = Alignment.CenterVertically) {
                Muted("Claude writes the review: add your API key first.", Modifier.weight(1f))
                TextButton(onClick = onSettings) { Text("Settings") }
            }
            if (writing) Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                Muted("Claude is reading your last two weeks…")
            }
            trouble?.let { t ->
                Text(t, color = MaterialTheme.colorScheme.error)
                if (hasKey && !writing) Button(onClick = { asks++ }) { Text("Retry") }
            }
            val s = saved
            if (s == null && !writing && trouble == null && hasKey) {
                Muted("No review yet.")
                Button(onClick = { asks++ }) { Text("Review my last two weeks") }
            }
            if (s != null) {
                Text(s.review.headline, style = MaterialTheme.typography.titleLarge)
                s.review.points.forEach { p ->
                    Card(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(p.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(p.text, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                if (s.review.focus.isNotBlank()) Card(
                    Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("For the coming week", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text(s.review.focus, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Muted(
                    "Written ${dm.format(java.time.Instant.ofEpochMilli(s.writtenAt).atZone(ZoneId.systemDefault()))} by ${s.model}, " +
                        "from ${s.meals} meals logged ${dm.format(s.from)}–${dm.format(s.to)}. Only as good as the log: " +
                        "meals not logged aren't in it.",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@Composable
private fun Muted(s: String, modifier: Modifier = Modifier, style: androidx.compose.ui.text.TextStyle = MaterialTheme.typography.bodyMedium) =
    Text(s, modifier, style = style, color = MaterialTheme.colorScheme.onSurfaceVariant)

private const val DAY_MS = 24L * 60 * 60 * 1000
private val dm = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
