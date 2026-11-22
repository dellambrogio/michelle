package dev.michelle.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.michelle.data.MealType
import java.time.format.DateTimeFormatter

private val hm = DateTimeFormatter.ofPattern("HH:mm")
private val dayFormat = DateTimeFormatter.ofPattern("EEE d MMM yyyy", java.util.Locale.ENGLISH)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MealScreen(id: Long, capture: String?, describe: Boolean, onDone: () -> Unit, onRetake: () -> Unit, onSettings: () -> Unit, onSaved: () -> Unit) {
    val vm: MealViewModel = viewModel()
    LaunchedEffect(Unit) { vm.start(id, capture, describe) }
    LaunchedEffect(vm.phase) { if (vm.phase == Phase.Done) onDone() }
    var confirmDelete by remember { mutableStateOf(false) }
    BackHandler { if (vm.isNew) vm.discard() else onDone() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (!vm.isNew) "Meal" else if (vm.isDescribed) "New meal, manual" else "New meal") },
                navigationIcon = {
                    IconButton(onClick = { if (vm.isNew) vm.discard() else onDone() }) {
                        Icon(if (vm.isNew) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, if (vm.isNew) "Discard" else "Back")
                    }
                },
                actions = {
                    if (!vm.isNew) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, "Delete") }
                },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).imePadding().verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (vm.photoPath != null) Photo(vm.photoPath, 800, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(RoundedCornerShape(12.dp)))
            when (val p = vm.phase) {
                Phase.Preparing, Phase.Analyzing -> Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(Modifier.size(24.dp))
                    Spacer(Modifier.size(12.dp))
                    Text(if (p == Phase.Preparing) "Preparing the photo…" else if (vm.isDescribed) "Claude is working it out…" else "Claude is looking at your meal…")
                }
                Phase.Describing -> {
                    OutlinedTextField(
                        vm.description, { vm.description = it }, Modifier.fillMaxWidth(),
                        label = { Text("What did you eat?") },
                        placeholder = { Text("e.g. 2 eggs, toast with butter, coffee with milk") },
                        minLines = 3,
                    )
                    WhenRow(vm)
                    // type the kcal, or let Claude estimate them from the description (then the items open)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            vm.manualKcal, { vm.manualKcal = it }, Modifier.weight(1f),
                            label = { Text("kcal") }, singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        )
                        OutlinedButton(onClick = vm::estimate, enabled = vm.description.isNotBlank()) { Text("Estimate") }
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
                        TextButton(onClick = vm::discard) { Text("Discard") }
                        Button(onClick = { vm.saveManual(onSaved) }, enabled = vm.manualKcalOk) { Text("Save") }
                    }
                }
                is Phase.NoFood -> Message((if (vm.isDescribed) "No food in that description." else "No food found in this photo.") + if (p.note.isNotBlank()) "\n${p.note}" else "") {
                    if (vm.isDescribed) Button(onClick = vm::redescribe) { Text("Change it") }
                    else Button(onClick = { vm.discard(); onRetake() }) { Text("Retake") }
                    OutlinedButton(onClick = vm::byHand) { Text("Enter by hand") }
                    TextButton(onClick = vm::discard) { Text("Discard") }
                }
                is Phase.Failed -> Message(p.trouble.message + (p.detail?.let { "\n$it" } ?: "")) {
                    if (p.trouble == dev.michelle.ai.Trouble.NO_KEY || p.trouble == dev.michelle.ai.Trouble.BAD_KEY) {
                        Button(onClick = onSettings) { Text("Settings") }
                        OutlinedButton(onClick = vm::analyze) { Text("Retry") }
                    } else Button(onClick = vm::analyze) { Text("Retry") }
                    TextButton(onClick = vm::discard) { Text("Discard") }
                }
                Phase.Editing -> Editor(vm, onSaved)
                Phase.Done -> {}
            }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this meal?") },
        text = { Text("Its photo goes too, and it's removed from Health Connect.") },
        confirmButton = { TextButton(onClick = { confirmDelete = false; vm.delete() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

@Composable
private fun Message(text: String, buttons: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(text)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { buttons() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun Editor(vm: MealViewModel, onSaved: () -> Unit) {
    val t = vm.totals
    Text("${t.kcal} kcal", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
    Text(macros(t), color = MaterialTheme.colorScheme.onSurfaceVariant)
    if (vm.note.isNotBlank()) Text(vm.note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

    OutlinedTextField(vm.title, { vm.title = it }, Modifier.fillMaxWidth(), label = { Text("Title") }, singleLine = true)
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
        MealType.entries.forEachIndexed { i, type ->
            SegmentedButton(
                selected = vm.mealType == type,
                onClick = { vm.pickType(type) },
                shape = SegmentedButtonDefaults.itemShape(i, MealType.entries.size),
            ) { Text(type.label, maxLines = 1) }
        }
    }
    WhenRow(vm)

    vm.items.forEach { item -> ItemCard(item) { vm.removeItem(item) } }
    OutlinedButton(onClick = vm::addItem) {
        Icon(Icons.Default.Add, null)
        Spacer(Modifier.size(6.dp))
        Text("Add item")
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End)) {
        if (vm.isNew) TextButton(onClick = vm::discard) { Text("Discard") }
        Button(onClick = { vm.save(onSaved) }, enabled = vm.items.isNotEmpty()) { Text("Save") }
    }

}

/** Date and time of the meal, default now (R6). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WhenRow(vm: MealViewModel) {
    var pickTime by remember { mutableStateOf(false) }
    var pickDate by remember { mutableStateOf(false) }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = { pickDate = true }) { Text(dayFormat.format(vm.time)) }
        OutlinedButton(onClick = { pickTime = true }) { Text(hm.format(vm.time)) }
        if (vm.time.toLocalDate() != java.time.LocalDate.now() || java.time.Duration.between(vm.time, java.time.LocalDateTime.now()).abs().toMinutes() > 1)
            TextButton(onClick = { vm.setWhen(java.time.LocalDateTime.now()) }) { Text("Now") }
    }
    if (vm.time.toLocalDate() != java.time.LocalDate.now()) Text(
        "Not today: you'll find it on its day with the arrows at the top of the home screen.",
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )

    if (pickDate) MealDatePicker(vm) { pickDate = false }
    if (pickTime) {
        val state = rememberTimePickerState(vm.time.hour, vm.time.minute, is24Hour = true)
        AlertDialog(
            onDismissRequest = { pickTime = false },
            confirmButton = {
                TextButton(onClick = {
                    vm.setWhen(vm.time.withHour(state.hour).withMinute(state.minute))
                    pickTime = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Cancel") } },
            text = { Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { TimePicker(state) } },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealDatePicker(vm: MealViewModel, onClose: () -> Unit) {
    // the date picker works in UTC milliseconds at midnight
    val state = rememberDatePickerState(
        initialSelectedDateMillis = vm.time.toLocalDate().atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli(),
    )
    DatePickerDialog(
        onDismissRequest = onClose,
        confirmButton = {
            TextButton(onClick = {
                state.selectedDateMillis?.let {
                    val d = java.time.Instant.ofEpochMilli(it).atZone(java.time.ZoneOffset.UTC).toLocalDate()
                    vm.setWhen(vm.time.with(d))
                }
                onClose()
            }) { Text("OK") }
        },
        dismissButton = { TextButton(onClick = onClose) { Text("Cancel") } },
    ) { DatePicker(state) }
}

@Composable
private fun ItemCard(item: EditItem, onRemove: () -> Unit) {
    val number = KeyboardOptions(keyboardType = KeyboardType.Decimal)
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(item.name, { item.name = it }, Modifier.weight(1f), label = { Text("Food") }, singleLine = true)
                IconButton(onClick = onRemove) { Icon(Icons.Default.Close, "Remove item") }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(item.portion, { item.portion = it }, Modifier.weight(1.4f), label = { Text("Portion") }, singleLine = true)
                OutlinedTextField(item.kcal, { item.kcal = it }, Modifier.weight(1f), label = { Text("kcal") }, singleLine = true, keyboardOptions = number)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedTextField(item.protein, { item.protein = it }, Modifier.weight(1f), label = { Text("Protein g") }, singleLine = true, keyboardOptions = number)
                OutlinedTextField(item.carbs, { item.carbs = it }, Modifier.weight(1f), label = { Text("Carbs g") }, singleLine = true, keyboardOptions = number)
                OutlinedTextField(item.fat, { item.fat = it }, Modifier.weight(1f), label = { Text("Fat g") }, singleLine = true, keyboardOptions = number)
            }
        }
    }
}
