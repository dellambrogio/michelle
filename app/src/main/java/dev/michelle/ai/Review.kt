package dev.michelle.ai

import android.content.Context
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import dev.michelle.Goal
import dev.michelle.Profile
import dev.michelle.data.MealType
import dev.michelle.data.MealWithItems
import dev.michelle.data.Totals
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

/** Claude's look back at the food log, in the shape of ReviewPrompt.SCHEMA. */
data class Review(
    val headline: String = "",
    val points: List<Point> = emptyList(),
    val focus: String = "",
) {
    data class Point(val title: String = "", val text: String = "")

    companion object {
        private val mapper = jacksonObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

        fun parse(json: String): Review? = runCatching { mapper.readValue<Review>(json) }.getOrNull()
            ?.takeIf { it.headline.isNotBlank() }
    }
}

object ReviewPrompt {
    /** How many days back the review reads: the last week, and the one before to compare. */
    const val DAYS = 14L

    const val SYSTEM = """You are a nutritionist looking back at someone's food log with them. The log comes from
a calorie-counting app: meals photographed or described, with estimated calories and macros, plus their
weight goal and weighings.

Write a short review for them, in English, in the second person, in plain friendly words. Base everything
on the log and cite it: averages, days, meals, specific foods that repeat. Look mostly at the last 7 days
and compare with the 7 before when it tells something. Things worth looking at: which meals and which
foods the calories come from, the days over the target and what made them so, protein, patterns in the
timing (late evenings, weekends), and how the weight is going against the plan.

"headline": one sentence, the main thing. "points": 3 to 5, each a short title and one to three
sentences; at least one of them about something that is going well. "focus": one concrete change for the
coming week, built from foods they actually eat (a swap, a portion, a habit), with what it saves.

A day with very few calories is most likely not fully logged: say so rather than praise it. With fewer
than 3 logged days, say in the headline that there's too little to go on and keep the rest short.
Don't moralize. No medical advice or diagnoses, nothing below the daily target, no extreme diets; if
something looks like a health concern, suggest talking to a doctor."""

    const val ASK = "Here is my log. How am I doing?"

    private val string = mapOf("type" to "string")

    val SCHEMA: Map<String, Any> = mapOf(
        "type" to "object",
        "properties" to mapOf(
            "headline" to string,
            "points" to mapOf(
                "type" to "array",
                "items" to mapOf(
                    "type" to "object",
                    "properties" to mapOf("title" to string, "text" to string),
                    "required" to listOf("title", "text"),
                    "additionalProperties" to false,
                ),
            ),
            "focus" to string,
        ),
        "required" to listOf("headline", "points", "focus"),
        "additionalProperties" to false,
    )

    private val day = DateTimeFormatter.ofPattern("yyyy-MM-dd EEE", Locale.ENGLISH)
    private val time = DateTimeFormatter.ofPattern("HH:mm", Locale.ENGLISH)

    /** The log as text for Claude: who, the goal, the weighings, then each day [from]..[to] with its meals. */
    fun log(
        today: LocalDate, from: LocalDate, to: LocalDate, zone: ZoneId,
        profile: Profile?, goal: Goal?, kcalTarget: Int?, weights: Map<LocalDate, Double>, meals: List<MealWithItems>,
    ): String = buildString {
        appendLine("Today: ${day.format(today)}. The log: ${day.format(from)} to ${day.format(to)}, whole days.")
        if (profile != null) appendLine(
            "Me: ${profile.sex.label.lowercase()}, ${profile.age(today)} years, ${profile.heightCm.roundToInt()} cm, " +
                "activity: ${profile.activity.label}."
        )
        if (goal != null) appendLine(
            "Goal: from ${kg(goal.startKg)} kg on ${goal.startDate} to ${kg(goal.targetKg)} kg on ${goal.targetDate} " +
                "(${kg(goal.paceKgPerWeek())} kg a week)."
        )
        appendLine("Daily calorie target: " + (kcalTarget?.let { "$it kcal" } ?: "not set"))
        val ws = weights.filterKeys { !it.isBefore(from) && !it.isAfter(to) }.toSortedMap()
        appendLine()
        if (ws.isEmpty()) appendLine("Weighings: none in these days.")
        else appendLine("Weighings, kg" + (if (goal != null) " (the plan for that day in brackets)" else "") + ":")
        ws.forEach { (d, w) -> appendLine("  ${day.format(d)}: ${kg(w)}" + (goal?.let { " (${kg(it.planKg(d))})" } ?: "")) }
        appendLine()
        appendLine("Meals (only what was logged in the app):")
        val byDay = meals.groupBy { Instant.ofEpochMilli(it.meal.time).atZone(zone).toLocalDate() }
        generateSequence(from) { it.plusDays(1) }.takeWhile { !it.isAfter(to) }.forEach { d ->
            val ms = byDay[d].orEmpty().sortedBy { it.meal.time }
            if (ms.isEmpty()) { appendLine("${day.format(d)}: nothing logged"); return@forEach }
            val t = Totals.ofMeals(ms)
            appendLine("${day.format(d)}: ${t.kcal} kcal, protein ${g(t.proteinG)}, carbs ${g(t.carbsG)}, fat ${g(t.fatG)}")
            ms.forEach { m ->
                val at = time.format(Instant.ofEpochMilli(m.meal.time).atZone(zone))
                val items = m.sorted.joinToString("; ") { "${it.name}" + (if (it.portion.isNotBlank()) " (${it.portion})" else "") + " ${it.kcal}" }
                appendLine("  $at ${MealType.of(m.meal.mealType).label} \"${m.meal.title}\" ${m.totals.kcal} kcal: $items")
            }
        }
    }

    private fun kg(v: Double) = "%.1f".format(Locale.ROOT, v)
    private fun g(v: Double) = "${v.roundToInt()} g"
}

/** The last review, kept on the phone so opening the screen costs nothing. */
class ReviewStore(context: Context) {
    private val prefs = context.getSharedPreferences("review", Context.MODE_PRIVATE)

    data class Saved(val review: Review, val writtenAt: Long, val from: LocalDate, val to: LocalDate, val meals: Int, val model: String)

    val saved: Saved?
        get() = runCatching {
            Saved(
                Review.parse(prefs.getString("json", null)!!)!!,
                prefs.getLong("writtenAt", 0),
                LocalDate.parse(prefs.getString("from", null)),
                LocalDate.parse(prefs.getString("to", null)),
                prefs.getInt("meals", 0),
                prefs.getString("model", "")!!,
            )
        }.getOrNull()

    fun save(json: String, writtenAt: Long, from: LocalDate, to: LocalDate, meals: Int, model: String) {
        prefs.edit().putString("json", json).putLong("writtenAt", writtenAt).putString("from", from.toString())
            .putString("to", to.toString()).putInt("meals", meals).putString("model", model).apply()
    }
}
