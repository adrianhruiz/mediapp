package com.medicapp.ui.history

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class WeekSummaryTest {

    @Test
    fun `nothing missed shows no coral count`() {
        assertNull(WeekSummary.missedLabel(0))
    }

    @Test
    fun `missed doses are counted in singular and plural`() {
        assertEquals("1 se escapó", WeekSummary.missedLabel(1))
        assertEquals("3 se escaparon", WeekSummary.missedLabel(3))
    }

    @Test
    fun `taken over due`() {
        assertEquals("0 de 5 tomas", WeekSummary.takenLabel(taken = 0, due = 5))
        assertEquals("Esta semana aún no tocaba ninguna toma", WeekSummary.takenLabel(taken = 0, due = 0))
    }
}
