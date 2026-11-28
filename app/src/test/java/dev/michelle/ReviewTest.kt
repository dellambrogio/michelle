package dev.michelle

import dev.michelle.ai.Review
import dev.michelle.ai.ReviewPrompt
import dev.michelle.data.Item
import dev.michelle.data.Meal
import dev.michelle.data.MealWithItems
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class ReviewTest {
    private val zone = ZoneOffset.UTC
    private fun meal(day: LocalDate, hour: Int, title: String, vararg kcal: Int) = MealWithItems(
        Meal(time = day.atTime(hour, 0).toInstant(zone).toEpochMilli(), mealType = "LUNCH", title = title,
            photoPath = null, rawAnalysis = null, model = null, createdAt = 0, updatedAt = 0),
        kcal.mapIndexed { i, k -> Item(name = "food$i", portion = "1 plate", kcal = k, proteinG = 10.0, carbsG = 20.0, fatG = 5.0, position = i) },
    )

    @Test fun log() {
        val from = LocalDate.of(2026, 11, 14)
        val to = LocalDate.of(2026, 11, 15)
        val goal = Goal(LocalDate.of(2026, 11, 1), 90.0, LocalDate.of(2027, 2, 1), 82.0)
        val text = ReviewPrompt.log(to.plusDays(1), from, to, zone, null, goal, 1750,
            mapOf(from to 88.0), listOf(meal(to, 13, "Pasta", 600, 150)))
        assertTrue(text, "2026-11-14 Sat: nothing logged" in text)
        assertTrue(text, "2026-11-15 Sun: 750 kcal, protein 20 g, carbs 40 g, fat 10 g" in text)
        assertTrue(text, "13:00 Lunch \"Pasta\" 750 kcal: food0 (1 plate) 600; food1 (1 plate) 150" in text)
        assertTrue(text, "2026-11-14 Sat: 88.0 (" in text)
        assertTrue(text, "Daily calorie target: 1750 kcal" in text)
    }

    @Test fun parse() {
        val r = Review.parse("""{"headline":"Good week","points":[{"title":"Dinners","text":"Big."}],"focus":"Less oil."}""")
        assertEquals("Dinners", r!!.points.single().title)
        assertNull(Review.parse("""{"headline":"","points":[],"focus":""}"""))
    }
}
