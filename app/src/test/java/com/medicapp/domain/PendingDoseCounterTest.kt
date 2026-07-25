package com.medicapp.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PendingDoseCounterTest {

    @Test
    fun `taking a dose leaves one less pending`() {
        assertEquals(5L, PendingDoseCounter.pendingAfterTaking(6))
    }

    @Test
    fun `the schedule closes with its last dose`() {
        val pendingAfter = PendingDoseCounter.pendingAfterTaking(1)

        assertEquals(0L, pendingAfter)
        assertTrue(PendingDoseCounter.closesSchedule(pendingAfter))
    }

    @Test
    fun `while doses remain the schedule stays open`() {
        assertFalse(PendingDoseCounter.closesSchedule(PendingDoseCounter.pendingAfterTaking(2)))
    }

    @Test
    fun `the count never goes below zero`() {
        // Si el contador se desajustara, la pauta no debe quedarse con dosis pendientes de menos
        // que cero ni dejar de cerrarse por ello.
        val pendingAfter = PendingDoseCounter.pendingAfterTaking(0)

        assertEquals(0L, pendingAfter)
        assertTrue(PendingDoseCounter.closesSchedule(pendingAfter))
    }
}
