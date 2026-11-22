package dev.michelle

import dev.michelle.data.Item
import dev.michelle.data.Meal
import dev.michelle.data.MealWithItems
import dev.michelle.data.Totals
import dev.michelle.ui.EditItem
import org.junit.Assert.assertEquals
import org.junit.Test

class TotalsTest {
    private fun item(kcal: Int, p: Double, c: Double, f: Double) = Item(name = "x", portion = "", kcal = kcal, proteinG = p, carbsG = c, fatG = f)
    private fun meal(vararg items: Item) = MealWithItems(Meal(time = 0, mealType = "LUNCH", title = "", photoPath = null, rawAnalysis = null, model = null, createdAt = 0, updatedAt = 0), items.toList())

    @Test fun empty() = assertEquals(Totals(), Totals.of(emptyList()))

    @Test fun items() = assertEquals(Totals(650, 25.0, 80.5, 22.0), Totals.of(listOf(item(400, 15.0, 60.5, 10.0), item(250, 10.0, 20.0, 12.0))))

    @Test fun day() = assertEquals(
        Totals(900, 30.0, 100.0, 30.0),
        Totals.ofMeals(listOf(meal(item(500, 20.0, 50.0, 20.0)), meal(item(300, 5.0, 30.0, 5.0), item(100, 5.0, 20.0, 5.0)))),
    )

    @Test fun editedNumbers() {
        // half-typed or odd input counts as 0, a comma works as a decimal point, kcal is rounded
        val i = EditItem("Bread", "1 slice", "80.6", "3,5", "", "-2").toItem()
        assertEquals(81, i.kcal)
        assertEquals(3.5, i.proteinG, 0.0)
        assertEquals(0.0, i.carbsG, 0.0)
        assertEquals(0.0, i.fatG, 0.0)
    }
}
