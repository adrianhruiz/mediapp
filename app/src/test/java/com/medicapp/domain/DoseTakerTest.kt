package com.medicapp.domain

import com.medicapp.data.repository.FakeMedicationHistoryRepository
import com.medicapp.data.repository.FakeScheduleRepository
import com.medicapp.model.DoseStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DoseTakerTest {

    private val schedules = FakeScheduleRepository()
    private val history = FakeMedicationHistoryRepository()
    private val doseTaker = DoseTaker(schedules, history)

    private suspend fun createSchedule() = schedules.createSchedule(
        nregistro = "62411",
        nombreMedicamento = "OMEPRAZOL NORMON 20 mg",
        fotoUrl = null,
        intervalHours = 24,
        totalDoses = 2,
        startAtMillis = 0L,
        reasonId = "r1",
        reasonNombre = "Acidez",
    )

    @Test
    fun `taking a dose records it in the history and links the entry`() = runTest {
        val (schedule, doses) = createSchedule()

        assertTrue(doseTaker.markTaken(schedule, doses.first()))

        val entry = history.entries.single()
        assertEquals("62411", entry.nregistro)
        val taken = schedules.dosesSnapshot(schedule.id).first()
        assertEquals(DoseStatus.TAKEN, taken.status)
        assertEquals(entry.id, taken.historyEntryId)
    }

    @Test
    fun `a dose that is no longer pending is left untouched`() = runTest {
        val (schedule, doses) = createSchedule()
        doseTaker.markTaken(schedule, doses.first())
        val alreadyTaken = schedules.dosesSnapshot(schedule.id).first()

        assertFalse(doseTaker.markTaken(schedule, alreadyTaken))
        assertEquals(1, history.entries.size)
    }
}
