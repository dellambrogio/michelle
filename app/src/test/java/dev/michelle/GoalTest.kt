package dev.michelle

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class GoalTest {
    // made-up test data: 80 kg on 20/7/1969, 0.5 kg a week for 84 days → 74 kg
    private val start = LocalDate.of(1969, 7, 20)
    private val goal = Goal(start, 80.0, start.plusDays(84), 74.0)

    @Test fun plan() {
        assertEquals(80.00, goal.planKg(start), 0.005)
        assertEquals(79.86, goal.planKg(LocalDate.of(1969, 7, 22)), 0.005)
        assertEquals(79.00, goal.planKg(LocalDate.of(1969, 8, 3)), 0.005)
        assertEquals(77.50, goal.planKg(LocalDate.of(1969, 8, 24)), 0.005)
    }

    @Test fun beforeAndAfter() {
        assertEquals(80.0, goal.planKg(start.minusDays(10)), 0.0)
        assertEquals(goal.targetKg, goal.planKg(start.plusDays(200)), 0.0)
    }

    @Test fun pace() = assertEquals(0.5, goal.paceKgPerWeek(), 1e-9)

    @Test fun sameDayGoal() {
        val g = Goal(start, 80.0, start, 78.0)
        assertEquals(80.0, g.planKg(start.minusDays(1)), 0.0)
        assertEquals(78.0, g.planKg(start), 0.0)
        assertEquals(0.0, g.paceKgPerWeek(), 0.0)
    }
}
