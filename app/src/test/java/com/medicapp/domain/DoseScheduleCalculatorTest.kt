package com.medicapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DoseScheduleCalculatorTest {

    @Test
    fun `computes evenly spaced dose times starting at the given instant`() {
        val start = 1_000_000_000_000L

        val times = DoseScheduleCalculator.computeDoseTimes(startAtMillis = start, intervalHours = 8, totalDoses = 3)

        assertEquals(
            listOf(start, start + 8 * 3_600_000L, start + 16 * 3_600_000L),
            times,
        )
    }

    @Test
    fun `a single dose is scheduled exactly at the start time`() {
        val start = 500_000L

        val times = DoseScheduleCalculator.computeDoseTimes(startAtMillis = start, intervalHours = 24, totalDoses = 1)

        assertEquals(listOf(start), times)
    }

    @Test
    fun `once a day for five days matches the dosing example from the allergy record`() {
        val times = DoseScheduleCalculator.computeDoseTimes(startAtMillis = 0L, intervalHours = 24, totalDoses = 5)

        assertEquals(5, times.size)
        assertEquals(0L, times.first())
        assertEquals(4 * 24 * 3_600_000L, times.last())
    }

    @Test
    fun `zero doses produces an empty schedule`() {
        val times = DoseScheduleCalculator.computeDoseTimes(startAtMillis = 0L, intervalHours = 8, totalDoses = 0)

        assertEquals(emptyList<Long>(), times)
    }
}
