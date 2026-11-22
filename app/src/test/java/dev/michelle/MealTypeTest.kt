package dev.michelle

import dev.michelle.data.MealType
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

class MealTypeTest {
    private fun at(h: Int, m: Int = 0) = MealType.fromTime(LocalTime.of(h, m))

    @Test fun breakfast() { assertEquals(MealType.BREAKFAST, at(5)); assertEquals(MealType.BREAKFAST, at(10, 59)) }
    @Test fun lunch() { assertEquals(MealType.LUNCH, at(11)); assertEquals(MealType.LUNCH, at(15, 59)) }
    @Test fun dinner() { assertEquals(MealType.DINNER, at(17)); assertEquals(MealType.DINNER, at(21, 59)) }
    @Test fun snack() { listOf(at(0), at(4, 59), at(16), at(16, 59), at(22), at(23, 59)).forEach { assertEquals(MealType.SNACK, it) } }
    @Test fun unknownNameIsSnack() = assertEquals(MealType.SNACK, MealType.of("BRUNCH"))
}
