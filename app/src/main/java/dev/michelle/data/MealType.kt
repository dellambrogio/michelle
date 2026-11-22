package dev.michelle.data

import java.time.LocalTime

enum class MealType(val label: String) {
    BREAKFAST("Breakfast"), LUNCH("Lunch"), DINNER("Dinner"), SNACK("Snack");

    companion object {
        /** breakfast 05:00–10:59, lunch 11:00–15:59, dinner 17:00–21:59, snack otherwise (R7) */
        fun fromTime(t: LocalTime): MealType = when (t.hour) {
            in 5..10 -> BREAKFAST
            in 11..15 -> LUNCH
            in 17..21 -> DINNER
            else -> SNACK
        }

        fun of(name: String): MealType = entries.firstOrNull { it.name == name } ?: SNACK
    }
}
