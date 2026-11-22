package dev.michelle

import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.michelle.ui.MealScreen
import dev.michelle.ui.MichelleTheme
import dev.michelle.ui.SettingsScreen
import dev.michelle.ui.TodayScreen
import dev.michelle.ui.TrendScreen
import kotlinx.coroutines.launch
import java.io.File

class MainActivity : ComponentActivity() {
    // tools/set-key.sh opens the settings with the key field focused (also when Michelle is already open)
    private var openKey by mutableStateOf(false)

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        if (intent.getStringExtra("open") == "key") openKey = true
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val app = application as App
        // debug builds only: start on a photo already in the app's cache (adb), for checking the analysis
        openKey = savedInstanceState == null && intent.getStringExtra("open") == "key"
        val debugPhoto = if (BuildConfig.DEBUG && savedInstanceState == null) intent.getStringExtra("photo") else null

        setContent {
            MichelleTheme {
                val nav = rememberNavController()
                val scope = rememberCoroutineScope()
                var capture by rememberSaveable { mutableStateOf<String?>(null) }
                val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
                    val file = capture?.let(::File)
                    if (ok && file != null && file.length() > 0) nav.navigate("meal/0?capture=${Uri.encode(file.path)}")
                    else file?.delete()
                    capture = null
                }
                val takePhoto = {
                    val (file, uri) = app.photos.newCapture()
                    capture = file.path
                    camera.launch(uri)
                }
                // ask for Health Connect once, after the first meal is saved (R15)
                val askHealth = rememberLauncherForActivityResult(app.health.permissionContract) {
                    app.settings.askedHealth = true
                    app.meals.syncSoon()
                }
                val onSaved: () -> Unit = {
                    scope.launch {
                        if (app.health.available && !app.settings.askedHealth && !app.health.granted()) {
                            askHealth.launch(dev.michelle.health.HealthWriter.PERMISSIONS)
                        }
                    }
                }
                val debugDone = remember { mutableStateOf(false) }
                LaunchedEffect(openKey) {
                    if (openKey) {
                        openKey = false
                        nav.navigate("settings?key=true") { popUpTo("today") }
                    }
                }
                LaunchedEffect(Unit) {
                    if (debugPhoto != null && !debugDone.value) {
                        debugDone.value = true
                        Log.i("michelle", "debug: analyzing $debugPhoto")
                        nav.navigate("meal/0?capture=${Uri.encode(debugPhoto)}")
                    }
                }

                NavHost(nav, startDestination = "today") {
                    composable("today") {
                        TodayScreen(
                            onCamera = takePhoto,
                            onDescribe = { nav.navigate("meal/0?describe=true") },
                            onOpen = { nav.navigate("meal/$it") },
                            onSettings = { nav.navigate("settings") },
                            onTrend = { nav.navigate("trend") },
                        )
                    }
                    composable("trend") { TrendScreen(onBack = { nav.popBackStack() }, onSettings = { nav.navigate("settings") }) }
                    composable(
                        "meal/{id}?capture={capture}&describe={describe}",
                        arguments = listOf(
                            navArgument("id") { type = NavType.LongType },
                            navArgument("capture") { type = NavType.StringType; nullable = true },
                            navArgument("describe") { type = NavType.BoolType; defaultValue = false },
                        ),
                    ) { entry ->
                        MealScreen(
                            id = entry.arguments?.getLong("id") ?: 0,
                            capture = entry.arguments?.getString("capture"),
                            describe = entry.arguments?.getBoolean("describe") == true,
                            onDone = { nav.popBackStack("today", inclusive = false) },
                            onRetake = { nav.popBackStack("today", inclusive = false); takePhoto() },
                            onSettings = { nav.navigate("settings") },
                            onSaved = onSaved,
                        )
                    }
                    composable(
                        "settings?key={key}",
                        arguments = listOf(navArgument("key") { type = NavType.BoolType; defaultValue = false }),
                    ) { entry ->
                        SettingsScreen(onBack = { nav.popBackStack() }, focusKey = entry.arguments?.getBoolean("key") == true)
                    }
                }
            }
        }
    }
}
