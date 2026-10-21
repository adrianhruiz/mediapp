package com.medicapp.ui.history

import com.medicapp.data.repository.ScheduledDose
import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule
import com.medicapp.model.ScheduleStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

class WeekPlannerTest {

    private val zone = ZoneOffset.UTC
    private val hour = 3_600_000L

    // Jueves 22 de octubre de 2026 a las 00:00 UTC; la semana empieza el lunes 19.
    private val thursday = 1_792_627_200_000L
    private val monday = LocalDate.of(2026, 10, 19)

    private val schedule = MedicationSchedule(
        id = "s", nregistro = "1", nombreMedicamento = "MEDICAMENTO", fotoUrl = null, intervalHours = 8,
        totalDoses = 10, startAtMillis = 0L, reasonId = "r", reasonNombre = "Motivo", status = ScheduleStatus.ACTIVE,
    )

    private fun dose(id: String, at: Long, status: DoseStatus) = ScheduledDose(
        schedule,
        MedicationDose(id, "s", 1, at, status, takenAtMillis = null, historyEntryId = null),
    )

    @Test
    fun `the week starts on monday`() {
        assertEquals(monday, WeekPlanner.weekStart(thursday + 10 * hour, zone))
        assertEquals(monday.minusWeeks(1), WeekPlanner.weekStart(thursday, zone, offsetWeeks = -1))
    }

    @Test
    fun `doses are grouped by day and past pending ones count as missed`() {
        val now = thursday + 10 * hour
        val plan = WeekPlanner.plan(
            monday,
            listOf(
                dose("taken", thursday - 2 * 24 * hour + 8 * hour, DoseStatus.TAKEN),
                dose("missed", thursday - 1 * 24 * hour + 14 * hour, DoseStatus.PENDING),
                dose("skipped", thursday - 1 * 24 * hour + 22 * hour, DoseStatus.SKIPPED),
                dose("later", thursday + 14 * hour, DoseStatus.PENDING),
            ),
            now,
            zone,
        )

        assertEquals(7, plan.days.size)
        assertEquals(listOf("taken"), plan.days[1].doses.map { it.dose.id })
        assertEquals(listOf("missed", "skipped"), plan.days[2].doses.map { it.dose.id })
        assertTrue(plan.days[3].isToday)
        assertEquals(listOf("missed"), plan.missed.map { it.dose.id })
        assertEquals(1, plan.taken)
        assertEquals(2, plan.due)
    }
}
