package com.medicapp.domain

import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ScheduleEditPlannerTest {

    private val hour = 3_600_000L
    private val start = 1_000_000_000_000L

    private fun dose(id: String, number: Int, at: Long, status: DoseStatus) = MedicationDose(
        id = id,
        scheduleId = "s1",
        doseNumber = number,
        scheduledAtMillis = at,
        status = status,
        takenAtMillis = if (status == DoseStatus.TAKEN) at else null,
        historyEntryId = null,
    )

    @Test
    fun `without taken doses every dose is regenerated from the next dose time`() {
        val doses = listOf(
            dose("d1", 1, start, DoseStatus.PENDING),
            dose("d2", 2, start + 8 * hour, DoseStatus.PENDING),
        )
        val next = start + 2 * hour

        val plan = ScheduleEditPlanner.plan(doses, intervalHours = 12, totalDoses = 3, nextDoseAtMillis = next)!!

        assertEquals(
            listOf(PlannedDose(1, next), PlannedDose(2, next + 12 * hour), PlannedDose(3, next + 24 * hour)),
            plan.newDoses,
        )
        assertEquals(emptyMap<String, Int>(), plan.renumberedKeptDoses)
        assertEquals(next, plan.startAtMillis)
        assertEquals(next + 24 * hour, plan.endAtMillis)
    }

    @Test
    fun `taken doses are kept and only the remaining doses are created after them`() {
        val doses = listOf(
            dose("d1", 1, start, DoseStatus.TAKEN),
            dose("d2", 2, start + 8 * hour, DoseStatus.TAKEN),
            dose("d3", 3, start + 16 * hour, DoseStatus.PENDING),
        )
        val next = start + 20 * hour

        val plan = ScheduleEditPlanner.plan(doses, intervalHours = 24, totalDoses = 5, nextDoseAtMillis = next)!!

        assertEquals(
            listOf(PlannedDose(3, next), PlannedDose(4, next + 24 * hour), PlannedDose(5, next + 48 * hour)),
            plan.newDoses,
        )
        assertEquals(emptyMap<String, Int>(), plan.renumberedKeptDoses)
        assertEquals(start, plan.startAtMillis)
        assertEquals(next + 48 * hour, plan.endAtMillis)
    }

    @Test
    fun `doses are numbered by time even when a taken dose falls after the new ones`() {
        val doses = listOf(
            dose("d1", 1, start, DoseStatus.PENDING),
            dose("d2", 2, start + 24 * hour, DoseStatus.TAKEN),
        )

        val plan = ScheduleEditPlanner.plan(doses, intervalHours = 8, totalDoses = 3, nextDoseAtMillis = start)!!

        assertEquals(listOf(PlannedDose(1, start), PlannedDose(2, start + 8 * hour)), plan.newDoses)
        assertEquals(mapOf("d2" to 3), plan.renumberedKeptDoses)
        assertEquals(start + 24 * hour, plan.endAtMillis)
    }

    @Test
    fun `the total must leave at least one dose to take`() {
        val doses = listOf(
            dose("d1", 1, start, DoseStatus.TAKEN),
            dose("d2", 2, start + 8 * hour, DoseStatus.TAKEN),
            dose("d3", 3, start + 16 * hour, DoseStatus.PENDING),
        )

        assertNull(ScheduleEditPlanner.plan(doses, intervalHours = 8, totalDoses = 2, nextDoseAtMillis = start))
        assertEquals(2, ScheduleEditPlanner.keptDoseCount(doses))
    }

    @Test
    fun `the interval must be at least one hour`() {
        val doses = listOf(dose("d1", 1, start, DoseStatus.PENDING))

        assertNull(ScheduleEditPlanner.plan(doses, intervalHours = 0, totalDoses = 2, nextDoseAtMillis = start))
    }
}
