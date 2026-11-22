package dev.michelle.trend

import dev.michelle.App
import dev.michelle.Goal
import dev.michelle.Profile
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.ZoneId

/** Everything the Today weight card, the trend screen and the suggestion need, read in one go. */
data class Progress(
    val today: LocalDate,
    val goal: Goal?,
    val profile: Profile?,
    val canReadWeight: Boolean,
    val weights: Map<LocalDate, Double>,           // measured, per day
    val trend: List<Pair<LocalDate, Double>>,      // smoothed, per day up to today
    val kcal: Map<LocalDate, Int>,                 // eaten per day (Michelle's meals)
) {
    val latest get() = weights.maxByOrNull { it.key }?.toPair()
    val trendKg get() = trend.lastOrNull()?.second
    /** The weight the calorie target works from: the latest weighing (the trend lags behind). */
    val currentKg get() = latest?.second ?: trendKg
    val target get() = dailyTarget(profile, goal, currentKg, today)
    val kcalTarget get() = (target as? Target.Value)?.kcal

    companion object {
        /** Read from [from] (default: the goal's start or 90 days back, whichever is earlier) to today. */
        suspend fun load(app: App, from: LocalDate? = null): Progress {
            val today = LocalDate.now()
            val goal = app.goals.goal
            val start = from ?: listOfNotNull(goal?.startDate, today.minusDays(90)).min()
            val weights = app.body.weights(start, today)
            return Progress(
                today = today,
                goal = goal,
                profile = app.profiles.profile,
                canReadWeight = app.body.canReadWeight(),
                weights = weights,
                trend = trend(weights, today, seed = goal?.let { it.startDate to it.startKg }),
                kcal = dailyKcal(app.meals.range(start, today).first(), ZoneId.systemDefault()),
            )
        }
    }
}
