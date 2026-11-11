package com.medicapp.ui.schedule

import com.medicapp.model.MedicationSchedule
import com.medicapp.model.ScheduleStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleTextsTest {

    private fun schedule(openEnded: Boolean) = MedicationSchedule(
        id = "s1", nregistro = "1", nombreMedicamento = "Vitamina D", fotoUrl = null,
        intervalHours = 24, totalDoses = if (openEnded) 0 else 10, startAtMillis = 0L,
        reasonId = "r1", reasonNombre = "Déficit", status = ScheduleStatus.ACTIVE, openEnded = openEnded,
    )

    @Test
    fun `an open-ended schedule says it lasts until closed instead of a dose count`() {
        assertEquals("Cada día · hasta que la cierres", ScheduleTexts.listSummary(schedule(openEnded = true)))
        assertEquals("Cada día · hasta que la cierres", ScheduleTexts.detailSummary(schedule(openEnded = true), takenCount = 3))
    }

    @Test
    fun `a fixed schedule keeps showing its dose count`() {
        assertEquals("Cada día · 10 tomas", ScheduleTexts.listSummary(schedule(openEnded = false)))
        assertEquals("Cada día · 3/10 tomas", ScheduleTexts.detailSummary(schedule(openEnded = false), takenCount = 3))
    }

    @Test
    fun `closing an open-ended schedule is called closing the schedule`() {
        assertEquals("Cerrar pauta", ScheduleTexts.closeLabel(schedule(openEnded = true)))
        assertEquals("Cerrar periodo", ScheduleTexts.closeLabel(schedule(openEnded = false)))
    }
}
