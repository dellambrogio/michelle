package dev.michelle

import android.app.Application
import dev.michelle.ai.Analyzer
import dev.michelle.data.Db
import dev.michelle.health.HealthWriter
import dev.michelle.photo.Photos
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Holds the app's parts: no DI framework for three screens. */
class App : Application() {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    val secrets by lazy { Secrets(this) }
    val settings by lazy { Settings(this) }
    val db by lazy { Db.open(this) }
    val photos by lazy { Photos(this) }
    val health by lazy { HealthWriter(this, db.meals()) }
    val meals by lazy { Meals(db.meals(), health, photos, scope) }
    val analyzer by lazy { Analyzer(secrets, settings) }

    override fun onCreate() {
        super.onCreate()
        meals.syncSoon()  // what couldn't be written to Health Connect last time (R17)
    }
}
