package com.medicapp.ui.today

import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule
import com.medicapp.model.ScheduleStatus
import com.medicapp.ui.components.MedicationNames
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

class TodayTimelineTextTest {

    private val zone = ZoneId.systemDefault()
    private val today = LocalDate.of(2026, 11, 11)
    private fun at(day: LocalDate, hour: Int, minute: Int = 0) =
        day.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()

    private val now = at(today, 13, 40)

    private fun dose(
        scheduledAt: Long,
        status: DoseStatus = DoseStatus.PENDING,
        takenAt: Long? = null,
        overdue: Boolean = false,
        name: String = "Amoxicilina",
        reason: String = "infección de garganta",
    ) = TodayDose(
        schedule = MedicationSchedule(
            id = "s1", nregistro = "1", nombreMedicamento = name, fotoUrl = null, intervalHours = 8,
            totalDoses = 9, startAtMillis = scheduledAt, reasonId = "r1", reasonNombre = reason,
            status = ScheduleStatus.ACTIVE,
        ),
        dose = MedicationDose("d1", "s1", 1, scheduledAt, status, takenAt, null),
        overdue = overdue,
    )

    @Test
    fun `a taken dose says when it was taken`() {
        assertEquals("Tomada a las 08:05", dose(at(today, 8), DoseStatus.TAKEN, takenAt = at(today, 8, 5)).timelineNote(now))
    }

    @Test
    fun `a dose still to come carries no note`() {
        assertNull(dose(at(today, 16)).timelineNote(now))
    }

    @Test
    fun `an overdue dose from today says since when it is due`() {
        assertEquals("Te toca desde las 09:00", dose(at(today, 9), overdue = true).timelineNote(now))
    }

    @Test
    fun `an overdue dose from yesterday names the day`() {
        assertEquals(
            "Sin tomar desde ayer a las 21:00",
            dose(at(today.minusDays(1), 21), overdue = true).timelineNote(now),
        )
    }

    @Test
    fun `a skipped dose says it was skipped`() {
        assertEquals("Saltada al cerrar la pauta", dose(at(today, 8), DoseStatus.SKIPPED).timelineNote(now))
    }

    @Test
    fun `the detail line joins dose and reason without a Para label`() {
        assertEquals("Para la acidez", dose(at(today, 14), reason = "la acidez").detailLine())
        val name = "Omeprazol Cinfa 20 mg cápsulas duras gastrorresistentes EFG"
        val detail = MedicationNames.split(name).second
        val expected = if (detail != null) "$detail · para la acidez" else "Para la acidez"
        assertEquals(expected, dose(at(today, 14), name = name, reason = "la acidez").detailLine())
    }
}
