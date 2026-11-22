package dev.michelle

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import dev.michelle.ui.MichelleTheme

/** What Health Connect shows when you ask why Michelle wants its permission (required by Health Connect). */
class PermissionsRationaleActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MichelleTheme {
                Surface(Modifier.fillMaxSize()) {
                    Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text("Michelle and your health data", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Michelle writes each meal you save (calories, protein, carbohydrate, fat, meal type and time) " +
                                "to Health Connect, so your other health apps can see what you ate. It never reads anything " +
                                "from Health Connect. Your photos and meals stay on this phone; only the photo is sent to " +
                                "Anthropic to estimate the calories."
                        )
                        Button(onClick = { finish() }) { Text("OK") }
                    }
                }
            }
        }
    }
}
