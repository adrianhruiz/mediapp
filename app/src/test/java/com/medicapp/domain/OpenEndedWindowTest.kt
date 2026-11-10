package com.medicapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class OpenEndedWindowTest {

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 1_000_000_000_000L

    @Test
    fun `a new open-ended schedule gets the doses of the next 7 days`() {
        val times = DoseScheduleCalculator.openEndedWindow(firstAtMillis = now, intervalHours = 24, nowMillis = now)

        assertEquals((0 until 7).map { now + it * day }, times)
    }

    @Test
    fun `a long interval still leaves one dose ahead`() {
        val first = now + hour

        val times = DoseScheduleCalculator.openEndedWindow(firstAtMillis = first, intervalHours = 672, nowMillis = now)

        assertEquals(listOf(first), times)
    }

    @Test
    fun `a first dose beyond the window is still created`() {
        val first = now + 20 * day

        assertEquals(listOf(first), DoseScheduleCalculator.openEndedWindow(first, intervalHours = 24, nowMillis = now))
    }

    @Test
    fun `replenishing continues after the last generated dose up to 7 days ahead`() {
        val last = now + 6 * day

        val times = DoseScheduleCalculator.openEndedReplenish(lastAtMillis = last, intervalHours = 24, nowMillis = now + day)

        assertEquals(listOf(now + 7 * day), times)
    }

    @Test
    fun `replenishing adds nothing while the window is full and a dose is ahead`() {
        val last = now + 6 * day

        assertEquals(emptyList<Long>(), DoseScheduleCalculator.openEndedReplenish(last, intervalHours = 24, nowMillis = now))
    }

    @Test
    fun `replenishing with a long interval adds the next dose once the last one has passed`() {
        val last = now - hour

        assertEquals(
            listOf(now - hour + 672 * hour),
            DoseScheduleCalculator.openEndedReplenish(last, intervalHours = 672, nowMillis = now),
        )
    }

    @Test
    fun `replenishing after a long gap skips the slots that already passed and keeps the rhythm`() {
        val last = now - 10 * day + hour

        val times = DoseScheduleCalculator.openEndedReplenish(last, intervalHours = 24, nowMillis = now)

        assertEquals((0 until 7).map { now + hour + it * day }, times)
    }
}
