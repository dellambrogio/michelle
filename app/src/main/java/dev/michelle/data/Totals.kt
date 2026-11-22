package dev.michelle.data

/** Sums of a meal's items, or of a day's meals (R8). */
data class Totals(val kcal: Int = 0, val proteinG: Double = 0.0, val carbsG: Double = 0.0, val fatG: Double = 0.0) {
    operator fun plus(o: Totals) = Totals(kcal + o.kcal, proteinG + o.proteinG, carbsG + o.carbsG, fatG + o.fatG)

    companion object {
        fun of(items: List<Item>) = items.fold(Totals()) { t, i -> t + Totals(i.kcal, i.proteinG, i.carbsG, i.fatG) }
        fun ofMeals(meals: List<MealWithItems>) = meals.fold(Totals()) { t, m -> t + m.totals }
    }
}
