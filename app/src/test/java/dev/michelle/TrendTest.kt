package dev.michelle

import dev.michelle.data.Item
import dev.michelle.data.Meal
import dev.michelle.data.MealWithItems
import dev.michelle.trend.Target
import dev.michelle.trend.bmr
import dev.michelle.trend.dailyKcal
import dev.michelle.trend.dailyTarget
import dev.michelle.trend.round50
import dev.michelle.trend.trend
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class TrendTest {
    private val d0 = LocalDate.of(1969, 7, 20)      // made-up test data

    @Test fun emaWithGaps() {
        val t = trend(mapOf(d0 to 80.0, d0.plusDays(1) to 79.0, d0.plusDays(3) to 78.0), d0.plusDays(4))
        assertEquals(5, t.size)
        assertEquals(80.0, t[0].second, 1e-9)            // starts at the first weight
        assertEquals(79.9, t[1].second, 1e-9)            // 80 + 0.1·(79 − 80)
        assertEquals(79.9, t[2].second, 1e-9)            // no weight: unchanged
        assertEquals(79.71, t[3].second, 1e-9)           // 79.9 + 0.1·(78 − 79.9)
        assertEquals(79.71, t[4].second, 1e-9)
        assertEquals(d0.plusDays(4), t.last().first)
    }

    @Test fun emaSeeded() {
        // starts at the plan's start; an older, heavier weight is left out; the start day's own weight isn't counted twice
        val t = trend(mapOf(d0.minusDays(30) to 88.0, d0 to 78.0, d0.plusDays(1) to 77.0), d0.plusDays(2), seed = d0 to 78.0)
        assertEquals(listOf(d0, d0.plusDays(1), d0.plusDays(2)), t.map { it.first })
        assertEquals(78.0, t[0].second, 1e-9)
        assertEquals(77.9, t[1].second, 1e-9)             // 78 + 0.1·(77 − 78)
        assertEquals(77.9, t[2].second, 1e-9)
    }

    @Test fun emaEmpty() = assertTrue(trend(emptyMap(), d0).isEmpty())

    private val zone = ZoneId.of("Europe/Zurich")
    private fun meal(at: ZonedDateTime, kcal: Int) = MealWithItems(
        Meal(time = at.toInstant().toEpochMilli(), mealType = "SNACK", title = "", photoPath = null, rawAnalysis = null, model = null, createdAt = 0, updatedAt = 0),
        listOf(Item(name = "x", portion = "", kcal = kcal, proteinG = 0.0, carbsG = 0.0, fatG = 0.0)),
    )

    @Test fun kcalPerLocalDayAcrossMidnight() {
        val k = dailyKcal(listOf(
            meal(d0.atTime(23, 50).atZone(zone), 300),
            meal(d0.plusDays(1).atTime(0, 10).atZone(zone), 200),
            meal(d0.plusDays(1).atTime(12, 0).atZone(zone), 700),
        ), zone)
        assertEquals(mapOf(d0 to 300, d0.plusDays(1) to 900), k)
    }

    private val today = d0.plusDays(6)
    private val goal = Goal(d0, 80.0, today.plusDays(77), 73.2)
    // made up: 40 on 26/7/1969, 180 cm, light activity
    private val man = Profile(Sex.MALE, LocalDate.of(1929, 3, 1), 180.0, Activity.LIGHT)

    @Test fun bmrMifflin() {
        assertEquals(1720.0, bmr(Sex.MALE, 79.0, 180.0, 40), 1e-9)    // 790 + 1125 − 200 + 5
        assertEquals(1554.0, bmr(Sex.FEMALE, 79.0, 180.0, 40), 1e-9)  // … − 161
    }

    @Test fun targetNormal() {
        // burned 1720 · 1.375 = 2365; deficit (79 − 73.2)·7700/77 = 580 → 1785 → 1800
        val t = dailyTarget(man, goal, 79.0, today) as Target.Value
        assertEquals(1800, t.kcal)
        assertEquals(2365, t.burned)
        assertEquals(580, t.deficit)
        assertEquals(77, t.daysLeft)
        assertFalse(t.floored)
    }

    @Test fun targetFloored() {
        val t = dailyTarget(man, goal.copy(targetDate = today.plusDays(20)), 79.0, today) as Target.Value
        assertTrue(t.floored)
        assertEquals(1500, t.kcal)
        val w = dailyTarget(man.copy(sex = Sex.FEMALE), goal.copy(targetDate = today.plusDays(20)), 79.0, today) as Target.Value
        assertEquals(1200, w.kcal)
    }

    @Test fun targetReached() {
        val t = dailyTarget(man, goal, 73.0, today) as Target.Value
        assertTrue(t.reached)
        assertEquals(round50(bmr(Sex.MALE, 73.0, 180.0, 40) * 1.375), t.kcal)
    }

    @Test fun targetNone() {
        assertTrue(dailyTarget(null, goal, 79.0, today) is Target.None)
        assertTrue(dailyTarget(man, null, 79.0, today) is Target.None)
        assertTrue(dailyTarget(man, goal.copy(targetDate = today), 79.0, today) is Target.None)
    }

    @Test fun targetWithoutWeightUsesStart() {
        val t = dailyTarget(man, goal, null, today) as Target.Value
        assertEquals((80.0 - 73.2) * 7700 / 77, t.deficit.toDouble(), 0.5)
    }

    @Test fun age() = assertEquals(40, man.age(today))

    @Test fun rounding() {
        assertEquals(1800, round50(1820.0)); assertEquals(1850, round50(1825.0)); assertEquals(1700, round50(1690.0))
    }
}
