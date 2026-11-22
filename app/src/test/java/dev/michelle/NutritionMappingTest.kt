package dev.michelle

import dev.michelle.data.Item
import dev.michelle.data.Meal
import dev.michelle.data.MealWithItems
import dev.michelle.health.HealthWriter
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import androidx.health.connect.client.records.MealType as HcMealType

class NutritionMappingTest {
    @Test fun mealToRecord() {
        val time = Instant.parse("1969-07-20T12:30:00Z").toEpochMilli()
        val m = MealWithItems(
            Meal(id = 42, time = time, mealType = "LUNCH", title = "Pasta and salad", photoPath = null, rawAnalysis = null, model = null, createdAt = 1, updatedAt = 777),
            listOf(
                Item(name = "Pasta", portion = "", kcal = 400, proteinG = 14.0, carbsG = 70.0, fatG = 6.0),
                Item(name = "Salad", portion = "", kcal = 120, proteinG = 2.0, carbsG = 6.0, fatG = 10.0),
            ),
        )
        val r = HealthWriter.record(m)
        assertEquals(Instant.ofEpochMilli(time), r.startTime)
        assertEquals(Instant.ofEpochMilli(time).plusSeconds(60), r.endTime)
        assertEquals(520.0, r.energy!!.inKilocalories, 0.001)
        assertEquals(16.0, r.protein!!.inGrams, 0.001)
        assertEquals(76.0, r.totalCarbohydrate!!.inGrams, 0.001)
        assertEquals(16.0, r.totalFat!!.inGrams, 0.001)
        assertEquals("Pasta and salad", r.name)
        assertEquals(HcMealType.MEAL_TYPE_LUNCH, r.mealType)
        assertEquals("meal-42", r.metadata.clientRecordId)
        assertEquals(777L, r.metadata.clientRecordVersion)
    }
}
