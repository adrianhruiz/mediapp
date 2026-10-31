package com.medicapp.ui.schedule

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class ScheduleDurationTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    @Test
    fun `presets give days times doses per day`() {
        assertEquals(21, ScheduleDuration.totalDoses(days = 7, intervalHours = 8))
        assertEquals(10, ScheduleDuration.totalDoses(days = 5, intervalHours = 12))
        assertEquals(3, ScheduleDuration.totalDoses(days = 3, intervalHours = 24))
    }

    @Test
    fun `an interval that does not divide 24 counts every dose that starts inside the window`() {
        // 2 días = 48 h; cada 10 h: 0, 10, 20, 30 y 40 caen dentro, la de 50 ya no.
        assertEquals(5, ScheduleDuration.totalDoses(days = 2, intervalHours = 10))
        // Cada 36 h en 2 días: 0 y 36.
        assertEquals(2, ScheduleDuration.totalDoses(days = 2, intervalHours = 36))
    }

    @Test
    fun `an interval longer than the window still gives the first dose`() {
        assertEquals(1, ScheduleDuration.totalDoses(days = 1, intervalHours = 72))
    }

    @Test
    fun `summary shows the total and when the last dose is`() {
        val last = LocalDateTime.of(2026, 11, 7, 21, 32).atZone(madrid).toInstant().toEpochMilli()

        assertEquals("21 tomas · última el 7 nov 21:32", ScheduleDuration.summary(21, last, madrid))
    }

    @Test
    fun `summary of a single dose says when it is`() {
        val only = LocalDateTime.of(2026, 10, 31, 9, 5).atZone(madrid).toInstant().toEpochMilli()

        assertEquals("1 toma · el 31 oct 09:05", ScheduleDuration.summary(1, only, madrid))
    }
}
