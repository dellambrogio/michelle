/*
 * The numbers behind Today and the trend screen, kept free of Android so they can be unit-tested (R32).
 */
package dev.michelle.trend

import dev.michelle.Goal
import dev.michelle.Profile
import dev.michelle.Sex
import dev.michelle.data.MealWithItems
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

const val KCAL_PER_KG = 7700.0
const val TREND_ALPHA = 0.1

/** kcal eaten per local day, from Michelle's meals (R30). */
fun dailyKcal(meals: List<MealWithItems>, zone: ZoneId): Map<LocalDate, Int> =
    meals.groupBy { Instant.ofEpochMilli(it.meal.time).atZone(zone).toLocalDate() }
        .mapValues { (_, ms) -> ms.sumOf { it.totals.kcal } }

/**
 * The smoothed weight (exponential moving average, Hacker's Diet style), one value a day up to [to]; a day
 * without a weight keeps the trend as it was (R29). With a [seed] (the plan's start: date and kg) it starts
 * there and older weights are left out, so it begins on the plan; without, at the first weight.
 */
fun trend(
    weights: Map<LocalDate, Double>, to: LocalDate, alpha: Double = TREND_ALPHA, seed: Pair<LocalDate, Double>? = null,
): List<Pair<LocalDate, Double>> {
    val (first, start) = seed ?: (weights.keys.minOrNull()?.let { it to weights.getValue(it) } ?: return emptyList())
    val out = mutableListOf<Pair<LocalDate, Double>>()
    var t = start
    var d = first
    if (seed != null) {          // the seed is the start: that day's own weighing doesn't count twice
        out += d to t
        d = d.plusDays(1)
    }
    while (!d.isAfter(to)) {
        if (d != first || seed != null) weights[d]?.let { t += alpha * (it - t) }
        out += d to t
        d = d.plusDays(1)
    }
    return out
}

/** Basal metabolic rate, Mifflin-St Jeor, kcal a day. */
fun bmr(sex: Sex, kg: Double, heightCm: Double, age: Int): Double =
    10 * kg + 6.25 * heightCm - 5 * age + if (sex == Sex.MALE) 5 else -161

fun floorKcal(sex: Sex) = if (sex == Sex.MALE) 1500 else 1200

sealed interface Target {
    /** [kcal] a day: about [burned] burned minus [deficit] a day for [daysLeft] days; [floored] when the plan needs less than is safe. */
    data class Value(val kcal: Int, val burned: Int, val deficit: Int, val daysLeft: Long, val reached: Boolean, val floored: Boolean) : Target {
        val explanation get() = when {
            reached -> "Target reached: about what you burn, $burned kcal a day."
            floored -> "You burn about $burned kcal a day; the plan needs $deficit less, below the safe minimum, so $kcal. The plan is too fast: a later date would be easier."
            else -> "You burn about $burned kcal a day; to reach the target in $daysLeft days, eat $deficit less."
        }
    }
    data class None(val reason: String) : Target
}

/**
 * The daily calorie target (R24): Mifflin-St Jeor × activity for the current weight, minus the deficit that
 * takes the current weight to the target weight by the target date, rounded to 50, never below the floor.
 */
fun dailyTarget(profile: Profile?, goal: Goal?, currentKg: Double?, today: LocalDate): Target {
    if (goal == null) return Target.None("Set a goal: target weight and date.")
    if (profile == null) return Target.None("Fill in your profile (sex, birth date, height, activity).")
    val kg = currentKg ?: goal.startKg
    val burned = bmr(profile.sex, kg, profile.heightCm, profile.age(today)) * profile.activity.factor
    val daysLeft = ChronoUnit.DAYS.between(today, goal.targetDate)
    if (kg <= goal.targetKg) return Target.Value(round50(burned), burned.roundToInt(), 0, daysLeft.coerceAtLeast(0), reached = true, floored = false)
    if (daysLeft <= 0) return Target.None("The target date has passed: set a new one.")
    val deficit = (kg - goal.targetKg) * KCAL_PER_KG / daysLeft
    val raw = round50(burned - deficit)
    val floor = floorKcal(profile.sex)
    return Target.Value(maxOf(raw, floor), burned.roundToInt(), deficit.roundToInt(), daysLeft, reached = false, floored = raw < floor)
}

fun round50(x: Double): Int = (x / 50).roundToInt() * 50
