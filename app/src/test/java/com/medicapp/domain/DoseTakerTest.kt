package com.medicapp.domain

import com.medicapp.data.repository.FakeMedicationHistoryRepository
import com.medicapp.data.repository.FakeScheduleRepository
import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose
import com.medicapp.model.ScheduleStatus
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

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 1_000_000_000_000L

    @Test
    fun `taking a dose of an open-ended schedule replenishes the window and schedules its alarms`() = runTest {
        val alarmed = mutableListOf<MedicationDose>()
        val taker = DoseTaker(schedules, history, now = { now + day }) { _, doses -> alarmed += doses }
        val (schedule, doses) = schedules.createSchedule(
            nregistro = "1", nombreMedicamento = "Vitamina D", fotoUrl = null, intervalHours = 24,
            totalDoses = 0, startAtMillis = now, reasonId = "r1", reasonNombre = "Déficit",
            openEnded = true, nowMillis = now,
        )
        assertEquals(7, doses.size)

        assertTrue(taker.markTaken(schedule, doses.first()))

        val after = schedules.dosesSnapshot(schedule.id)
        assertEquals(8, after.size)
        assertEquals(8, after.last().doseNumber)
        assertEquals(now + 7 * day, after.last().scheduledAtMillis)
        assertEquals(listOf(after.last()), alarmed)
        assertEquals(ScheduleStatus.ACTIVE, schedules.getSchedule(schedule.id)!!.status)
    }

    @Test
    fun `taking the only pending dose of an open-ended schedule does not close it`() = runTest {
        val taker = DoseTaker(schedules, history, now = { now })
        val (schedule, doses) = schedules.createSchedule(
            nregistro = "1", nombreMedicamento = "Vitamina D", fotoUrl = null, intervalHours = 672,
            totalDoses = 0, startAtMillis = now + hour, reasonId = "r1", reasonNombre = "Déficit",
            openEnded = true, nowMillis = now,
        )
        assertEquals(1, doses.size)

        taker.markTaken(schedule, doses.single())

        assertEquals(ScheduleStatus.ACTIVE, schedules.getSchedule(schedule.id)!!.status)
    }

    @Test
    fun `taking the last dose of a fixed schedule still closes it without replenishing`() = runTest {
        val alarmed = mutableListOf<MedicationDose>()
        val taker = DoseTaker(schedules, history, now = { now }) { _, doses -> alarmed += doses }
        val (schedule, doses) = createSchedule()

        doses.forEach { taker.markTaken(schedule, it) }

        assertEquals(ScheduleStatus.CLOSED, schedules.getSchedule(schedule.id)!!.status)
        assertEquals(2, schedules.dosesSnapshot(schedule.id).size)
        assertTrue(alarmed.isEmpty())
    }

    @Test
    fun `closing an open-ended schedule turns its pending doses into skipped`() = runTest {
        val (schedule, _) = schedules.createSchedule(
            nregistro = "1", nombreMedicamento = "Vitamina D", fotoUrl = null, intervalHours = 24,
            totalDoses = 0, startAtMillis = now, reasonId = "r1", reasonNombre = "Déficit",
            openEnded = true, nowMillis = now,
        )

        schedules.closeSchedule(schedule.id)

        assertEquals(ScheduleStatus.CLOSED, schedules.getSchedule(schedule.id)!!.status)
        assertTrue(schedules.dosesSnapshot(schedule.id).all { it.status == DoseStatus.SKIPPED })
        assertTrue(schedules.replenishOpenEndedDoses(schedule.id, now + 30 * day).isEmpty())
    }
}
