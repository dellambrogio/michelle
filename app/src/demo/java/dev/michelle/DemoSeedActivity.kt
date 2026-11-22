package dev.michelle

import android.app.Activity
import android.os.Bundle
import android.util.Log
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Mass
import dev.michelle.data.Item
import dev.michelle.data.Meal
import dev.michelle.data.MealType
import kotlinx.coroutines.launch
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.round
import kotlin.math.sin

/**
 * Demo builds only: fills an empty app with made-up meals, a goal and a profile, and Health Connect with
 * made-up weights, for the README screenshots. Photos are expected in files/demo/{breakfast,lunch}.jpg.
 */
class DemoSeedActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as App
        app.scope.launch {
            runCatching { seed(app) }.onFailure { Log.e("michelle", "demo seed failed", it) }
            runOnUiThread { finish() }
        }
    }

    private suspend fun seed(app: App) {
        if (app.secrets.apiKey == null) app.secrets.apiKey = "sk-ant-demo"  // a placeholder, so the buttons show
        if (app.goals.goal != null) return  // seeded already
        val dao = app.db.meals()
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now()
        val start = today.minusDays(42)
        app.goals.goal = Goal(start, 84.0, today.plusDays(70), 77.0)
        app.profiles.profile = Profile(Sex.MALE, LocalDate.of(1969, 7, 20), 178.0, dev.michelle.Activity.LIGHT)

        fun at(d: LocalDate, h: Int, m: Int) = d.atTime(LocalTime.of(h, m)).atZone(zone).toInstant().toEpochMilli()
        suspend fun meal(time: Long, title: String, photo: String?, vararg items: Item) {
            val path = photo?.let { name ->
                val src = File(filesDir, "demo/$name.jpg")
                if (!src.exists()) null else File(filesDir, "photos/demo-$name.jpg").also { src.copyTo(it, overwrite = true) }.path
            }
            val type = MealType.fromTime(java.time.Instant.ofEpochMilli(time).atZone(zone).toLocalTime())
            dao.save(Meal(time = time, mealType = type.name, title = title, photoPath = path, rawAnalysis = null,
                model = null, hcSynced = true, createdAt = time, updatedAt = time), items.toList())
        }
        fun item(name: String, portion: String, kcal: Int, p: Double, c: Double, f: Double) = Item(name = name, portion = portion, kcal = kcal, proteinG = p, carbsG = c, fatG = f)

        // past days: one entry a day, around the target, a bit over now and then
        var d = start
        while (d.isBefore(today)) {
            val n = start.until(d, java.time.temporal.ChronoUnit.DAYS).toInt()
            val kcal = (1790 + 190 * sin(n * 1.7) + 90 * sin(n * 0.45)).toInt() / 10 * 10
            meal(at(d, 20, 0), "Meals of the day", null, item("Meals of the day", "whole day", kcal, kcal * 0.05, kcal * 0.11, kcal * 0.035))
            d = d.plusDays(1)
        }
        // today
        meal(at(today, 7, 45), "Yogurt with banana and raisins", "breakfast",
            item("Plain yogurt", "200 g", 130, 7.0, 9.5, 7.0),
            item("Banana", "1 medium, sliced", 105, 1.3, 27.0, 0.4),
            item("Raisins", "30 g", 90, 0.9, 24.0, 0.1))
        meal(at(today, 12, 30), "Farfalle with pesto and cherry tomatoes", "lunch",
            item("Farfalle, cooked", "220 g", 350, 12.5, 69.0, 2.0),
            item("Basil pesto", "2 tbsp", 160, 2.5, 2.0, 16.0),
            item("Cherry tomatoes", "8", 25, 1.2, 5.0, 0.3),
            item("Rocket", "a handful", 5, 0.5, 0.7, 0.1))
        meal(at(today, 16, 10), "Apple and almonds", null,
            item("Apple", "1 medium", 95, 0.5, 25.0, 0.3),
            item("Almonds", "20 g", 115, 4.2, 4.3, 10.0))

        // weights: every day or two, losing about 0.5 kg a week, with day-to-day noise
        val hc = HealthConnectClient.getOrCreate(this)
        val weights = (0..42).filter { it % 7 != 3 && it % 5 != 2 }.map { n ->
            val day = start.plusDays(n.toLong())
            val kg = 84.0 - n * 0.072 + 0.45 * sin(n * 2.3) + 0.2 * sin(n * 0.9)
            val t = day.atTime(7, 10).atZone(zone)
            WeightRecord(t.toInstant(), t.offset, Mass.kilograms(round(kg * 10) / 10), Metadata.manualEntry("demo-weight-$n"))
        }
        hc.insertRecords(weights)
        Log.i("michelle", "demo seed: ${weights.size} weights")
    }
}
