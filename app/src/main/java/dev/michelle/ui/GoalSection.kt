package dev.michelle.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import dev.michelle.Activity
import dev.michelle.App
import dev.michelle.Goal
import dev.michelle.Profile
import dev.michelle.Sex
import dev.michelle.health.BodyReader
import dev.michelle.trend.Progress
import dev.michelle.trend.Target
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dmy = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/**
 * Settings → Goal: target weight and date (the start is the latest weight when saved), the profile for the
 * calorie formula, and the daily target worked out from them (R22–R25).
 */
@Composable
fun GoalSection() {
    val app = LocalContext.current.applicationContext as App
    val scope = rememberCoroutineScope()
    var saved by remember { mutableStateOf(app.goals.goal) }
    var targetDate by remember { mutableStateOf(saved?.targetDate ?: LocalDate.now().plusDays(90)) }
    var targetKg by remember { mutableStateOf(saved?.targetKg?.let(::plain) ?: "") }
    var typedKg by remember { mutableStateOf("") }               // only when Health Connect has no weight
    val p0 = remember { app.profiles.profile }
    var sex by remember { mutableStateOf(p0?.sex) }
    var birth by remember { mutableStateOf(p0?.birthDate ?: LocalDate.now().minusYears(40)) }
    var height by remember { mutableStateOf(p0?.heightCm?.let(::plain) ?: "") }
    var activity by remember { mutableStateOf(p0?.activity ?: Activity.LIGHT) }
    var progress by remember { mutableStateOf<Progress?>(null) }
    var loads by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf("") }
    LaunchedEffect(loads) {
        progress = Progress.load(app)
        if (height.isBlank()) app.body.heightCm()?.let { height = "%.0f".format(it) }
    }
    val ask = rememberLauncherForActivityResult(app.health.permissionContract) { loads++ }

    val p = progress
    val latest = p?.latest
    val tKg = targetKg.num()
    val profile = sex?.let { s -> height.num()?.takeIf { it in 100.0..250.0 }?.let { Profile(s, birth, it, activity) } }
    // the goal as it would be saved now: from the latest weight (or the typed one) to the target
    val draft = tKg?.let { t ->
        // the plan starts today, from the latest weight (or the typed one)
        (latest?.second ?: typedKg.num())?.let { kg -> Goal(LocalDate.now(), kg, targetDate, t) }?.takeIf { targetDate.isAfter(it.startDate) }
    }
    val shown = draft ?: saved

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DateField("Target", targetDate, Modifier.weight(1f)) { targetDate = it }
            OutlinedTextField(targetKg, { targetKg = it }, Modifier.weight(1f), label = { Text("Target kg") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
        }
        when {
            p == null -> {}
            latest != null -> Note("Starts today from your latest weight: ${plain(latest.second)} kg on ${dmy.format(latest.first)} (Health Connect).")
            else -> {
                Note(if (p.canReadWeight) "No weight in Health Connect yet: type today's." else "Michelle can't read your weight yet: allow it, or type today's.")
                OutlinedTextField(typedKg, { typedKg = it }, Modifier.fillMaxWidth(), label = { Text("Current kg") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        }
        if (draft != null) Note("%.2f kg a week, %d days".format(draft.paceKgPerWeek(), ChronoUnit.DAYS.between(draft.startDate, draft.targetDate)))
        else if (tKg != null && !targetDate.isAfter(LocalDate.now())) Text("The target date must be in the future.", color = MaterialTheme.colorScheme.error)
        saved?.let { Note("Saved: ${plain(it.startKg)} kg on ${dmy.format(it.startDate)} → ${plain(it.targetKg)} kg on ${dmy.format(it.targetDate)}.") }

        Text("Profile, for the calories", style = MaterialTheme.typography.titleSmall)
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Sex.entries.forEachIndexed { i, s ->
                SegmentedButton(sex == s, { sex = s }, SegmentedButtonDefaults.itemShape(i, Sex.entries.size)) { Text(s.label) }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            DateField("Born", birth, Modifier.weight(1f)) { birth = it }
            OutlinedTextField(height, { height = it }, Modifier.weight(1f), label = { Text("Height cm") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        }
        Activity.entries.forEach { a ->
            Row(Modifier.fillMaxWidth().selectable(activity == a) { activity = a }, verticalAlignment = Alignment.CenterVertically) {
                RadioButton(activity == a, null)
                Text(a.label, Modifier.padding(start = 8.dp))
            }
        }

        // the daily target from what's on screen
        if (p != null) {
            val t = p.copy(goal = shown, profile = profile ?: p.profile).target
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    when (t) {
                        is Target.Value -> {
                            Text("${t.kcal} kcal a day", style = MaterialTheme.typography.titleLarge,
                                color = if (t.floored) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                            Note(t.explanation)
                        }
                        is Target.None -> Note(t.reason)
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                app.goals.goal = draft
                app.profiles.profile = profile
                saved = draft
                message = "Saved."
                scope.launch {
                    if (app.health.available && !app.body.allGranted()) ask.launch(BodyReader.PERMISSIONS) else loads++
                }
            }, enabled = draft != null && profile != null && (draft != saved || profile != app.profiles.profile)) { Text("Save") }
            if (app.health.available && p != null && !p.canReadWeight) {
                OutlinedButton(onClick = { ask.launch(BodyReader.PERMISSIONS) }) { Text("Allow reading weight") }
            }
        }
        if (profile == null) Note("To save: target, sex, height and activity.")
        if (message.isNotEmpty()) Note(message)
    }
}

@Composable
private fun Note(s: String) = Text(s, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

private fun String.num() = trim().replace(',', '.').toDoubleOrNull()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateField(label: String, date: LocalDate, modifier: Modifier, onPick: (LocalDate) -> Unit) {
    var open by remember { mutableStateOf(false) }
    OutlinedButton(onClick = { open = true }, modifier) { Text("$label: ${dmy.format(date)}") }
    if (open) {
        // the date picker works in UTC milliseconds at midnight
        val state = rememberDatePickerState(initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(
            onDismissRequest = { open = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { onPick(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
                    open = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { open = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
