package dev.michelle

import android.content.Context
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/** The weight plan: a straight line from the start to the target (R22, R23). */
data class Goal(
    val startDate: LocalDate,
    val startKg: Double,
    val targetDate: LocalDate,
    val targetKg: Double,
) {
    private val days get() = ChronoUnit.DAYS.between(startDate, targetDate)

    /** What the plan says for day [d]: the start weight before it starts, the target weight after it ends. */
    fun planKg(d: LocalDate): Double = when {
        days <= 0 || !d.isAfter(startDate) -> if (days <= 0 && !d.isBefore(targetDate)) targetKg else startKg
        !d.isBefore(targetDate) -> targetKg
        else -> startKg + (targetKg - startKg) * ChronoUnit.DAYS.between(startDate, d) / days
    }

    /** Kilos a week the plan loses (negative if it gains). */
    fun paceKgPerWeek(): Double = if (days <= 0) 0.0 else (startKg - targetKg) / days * 7
}

enum class Sex(val label: String) { MALE("Male"), FEMALE("Female") }

enum class Activity(val label: String, val factor: Double) {
    SEDENTARY("Sedentary (desk, little exercise)", 1.2),
    LIGHT("Light (exercise 1–3 days a week)", 1.375),
    MODERATE("Moderate (exercise 3–5 days a week)", 1.55),
    VERY("Very active (hard exercise 6–7 days)", 1.725),
}

/** What the calorie formula needs, entered once (R24). */
data class Profile(val sex: Sex, val birthDate: LocalDate, val heightCm: Double, val activity: Activity) {
    fun age(today: LocalDate) = ChronoUnit.YEARS.between(birthDate, today).toInt()
}

/** The goal and the profile, kept in the app's preferences; null until they're set. */
class GoalStore(context: Context) {
    private val prefs = context.getSharedPreferences("goal", Context.MODE_PRIVATE)

    var goal: Goal?
        get() = runCatching {
            Goal(
                LocalDate.parse(prefs.getString("startDate", null)),
                prefs.getString("startKg", null)!!.toDouble(),
                LocalDate.parse(prefs.getString("targetDate", null)),
                prefs.getString("targetKg", null)!!.toDouble(),
            )
        }.getOrNull()
        set(g) {
            prefs.edit().apply {
                if (g == null) listOf("startDate", "startKg", "targetDate", "targetKg").forEach(::remove) else {
                    putString("startDate", g.startDate.toString())
                    putString("startKg", g.startKg.toString())
                    putString("targetDate", g.targetDate.toString())
                    putString("targetKg", g.targetKg.toString())
                }
            }.apply()
        }
}

class ProfileStore(context: Context) {
    private val prefs = context.getSharedPreferences("goal", Context.MODE_PRIVATE)

    var profile: Profile?
        get() = runCatching {
            Profile(
                Sex.valueOf(prefs.getString("sex", null)!!),
                LocalDate.parse(prefs.getString("birthDate", null)),
                prefs.getString("heightCm", null)!!.toDouble(),
                Activity.valueOf(prefs.getString("activity", null)!!),
            )
        }.getOrNull()
        set(p) {
            prefs.edit().apply {
                if (p == null) listOf("sex", "birthDate", "heightCm", "activity").forEach(::remove) else {
                    putString("sex", p.sex.name)
                    putString("birthDate", p.birthDate.toString())
                    putString("heightCm", p.heightCm.toString())
                    putString("activity", p.activity.name)
                }
            }.apply()
        }
}
