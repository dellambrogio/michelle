package dev.michelle.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import kotlinx.coroutines.delay
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import dev.michelle.App
import dev.michelle.Settings

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit, focusKey: Boolean = false) {
    val app = LocalContext.current.applicationContext as App
    var hasKey by remember { mutableStateOf(app.secrets.apiKey != null) }
    var key by remember { mutableStateOf("") }
    var model by remember { mutableStateOf(app.settings.model) }
    var granted by remember { mutableStateOf<Boolean?>(null) }
    var checks by remember { mutableIntStateOf(0) }
    val unsynced by app.meals.unsyncedCount.collectAsState(0)
    val keyFocus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    // opened by tools/set-key.sh: focus the key field once the window can take it, keyboard up
    if (focusKey) LaunchedEffect(Unit) {
        repeat(10) {
            delay(150)
            if (runCatching { keyFocus.requestFocus() }.isSuccess) {
                keyboard?.show()
                return@LaunchedEffect
            }
        }
    }
    val saveKey = {
        if (key.isNotBlank()) {
            app.secrets.apiKey = key
            key = ""
            hasKey = app.secrets.apiKey != null
        }
    }
    LaunchedEffect(checks) { granted = app.health.granted() }
    val ask = rememberLauncherForActivityResult(app.health.permissionContract) {
        app.settings.askedHealth = true
        checks++
        app.meals.syncSoon()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
    ) { pad ->
        Column(
            Modifier.fillMaxSize().padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Section("Anthropic API key")
            Text(
                if (hasKey) "A key is saved (encrypted on this phone)." else "No key yet. Get one at console.anthropic.com → API Keys.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                key, { key = it }, Modifier.fillMaxWidth().focusRequester(keyFocus),
                label = { Text(if (hasKey) "Replace the key" else "Key (sk-ant-…)") },
                singleLine = true, visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { saveKey() }),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { saveKey() }, enabled = key.isNotBlank()) { Text("Save key") }
                if (hasKey) TextButton(onClick = { app.secrets.apiKey = null; hasKey = false }) { Text("Remove key") }
            }

            HorizontalDivider()
            Section("Model")
            Settings.MODELS.forEach { m ->
                Row(
                    Modifier.fillMaxWidth().selectable(m == model) { model = m; app.settings.model = m },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(m == model, null)
                    Column(Modifier.padding(start = 8.dp)) {
                        Text(m)
                        Text(MODEL_NOTES[m].orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            HorizontalDivider()
            Section("Health Connect")
            Text(
                when {
                    !app.health.available -> "Health Connect isn't available on this phone."
                    granted == true -> "Meals are written to Health Connect as nutrition." +
                        if (unsynced > 0) " $unsynced not written yet." else ""
                    granted == false -> "Michelle may not write to Health Connect yet; meals are kept only here."
                    else -> ""
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (app.health.available) Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (granted == false) Button(onClick = { ask.launch(dev.michelle.health.HealthWriter.PERMISSIONS) }) { Text("Allow") }
                if (granted == true) OutlinedButton(onClick = { app.meals.syncSoon() }, enabled = unsynced > 0) { Text("Sync now") }
            }
        }
    }
}

private val MODEL_NOTES = mapOf(
    "claude-opus-5-5" to "Best estimates, about 2–4 cents a meal",
    "claude-sonnet-5-5" to "Good and a bit cheaper",
    "claude-haiku-5-5" to "Quickest and a fraction of a cent, may be less accurate",
)

@Composable
private fun Section(title: String) = Text(title, style = MaterialTheme.typography.titleMedium)
