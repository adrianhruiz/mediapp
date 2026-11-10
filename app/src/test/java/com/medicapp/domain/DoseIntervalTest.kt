package com.medicapp.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class DoseIntervalTest {

    @Test
    fun `toHours converts amount and unit to hours`() {
        assertEquals(12, DoseInterval.toHours(12, DoseInterval.Unit.HOURS))
        assertEquals(48, DoseInterval.toHours(2, DoseInterval.Unit.DAYS))
        assertEquals(336, DoseInterval.toHours(2, DoseInterval.Unit.WEEKS))
    }

    @Test
    fun `split picks the largest unit that divides exactly`() {
        assertEquals(1 to DoseInterval.Unit.WEEKS, DoseInterval.split(168))
        assertEquals(2 to DoseInterval.Unit.WEEKS, DoseInterval.split(336))
        assertEquals(4 to DoseInterval.Unit.WEEKS, DoseInterval.split(672))
        assertEquals(2 to DoseInterval.Unit.DAYS, DoseInterval.split(48))
        assertEquals(1 to DoseInterval.Unit.DAYS, DoseInterval.split(24))
        assertEquals(36 to DoseInterval.Unit.HOURS, DoseInterval.split(36))
        assertEquals(12 to DoseInterval.Unit.HOURS, DoseInterval.split(12))
    }

    @Test
    fun `label reads naturally in singular and plural`() {
        assertEquals("Cada 8 h", DoseInterval.label(8))
        assertEquals("Cada 12 h", DoseInterval.label(12))
        assertEquals("Cada 36 h", DoseInterval.label(36))
        assertEquals("Cada día", DoseInterval.label(24))
        assertEquals("Cada 2 días", DoseInterval.label(48))
        assertEquals("Cada semana", DoseInterval.label(168))
        assertEquals("Cada 2 semanas", DoseInterval.label(336))
        assertEquals("Cada 4 semanas", DoseInterval.label(672))
    }

    @Test
    fun `presets cover the frequencies offered in the form`() {
        assertEquals(listOf(8, 12, 24, 48, 168, 336, 672), DoseInterval.presets)
    }
}
