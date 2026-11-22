package dev.michelle

import dev.michelle.ai.Analysis
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisParseTest {
    @Test fun good() {
        val a = Analysis.parse(
            """{"food":true,"title":"Spaghetti al pomodoro","items":[
               {"name":"Spaghetti","portion":"200 g cooked","kcal":315,"protein_g":11,"carbs_g":62,"fat_g":2},
               {"name":"Olive oil","portion":"1 tbsp","kcal":120,"protein_g":0,"carbs_g":0,"fat_g":14}],
               "note":"Assumed one tablespoon of oil."}"""
        )!!
        assertTrue(a.food)
        assertEquals("Spaghetti al pomodoro", a.title)
        assertEquals(2, a.items.size)
        assertEquals(315.0, a.items[0].kcal, 0.0)
        assertEquals(14.0, a.items[1].fatG, 0.0)
        assertEquals("1 tbsp", a.items[1].portion)
    }

    @Test fun noFood() {
        val a = Analysis.parse("""{"food":false,"title":"","items":[],"note":"A computer keyboard."}""")!!
        assertFalse(a.food)
        assertEquals("A computer keyboard.", a.note)
    }

    @Test fun bad() {
        assertNull(Analysis.parse("Sorry, I can't tell."))
        assertNull(Analysis.parse("""{"title":"x"}"""))                      // no "food"
        assertNull(Analysis.parse("""{"food":true,"title":"x","items":[],"note":""}"""))  // food but nothing in it
    }
}
