package com.medicapp.ui.schedule

import android.app.AlarmManager
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.medicapp.data.repository.FakeMedicationHistoryRepository
import com.medicapp.data.repository.FakeScheduleRepository
import com.medicapp.domain.DoseTaker
import com.medicapp.model.DoseStatus
import com.medicapp.notifications.DoseAlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

/** Cerrar una pauta quita las alarmas de todas las tomas que pasa a omitidas, también las repuestas. */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class CloseScheduleAlarmsTest {

    private val dispatcher = StandardTestDispatcher()
    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 1_000_000_000_000L

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val schedules = FakeScheduleRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Pauta sin fin con sus alarmas y una toma repuesta (con su alarma) tras marcar la primera. */
    private suspend fun openEndedWithRefill(): String {
        val (schedule, doses) = schedules.createSchedule(
            nregistro = "1", nombreMedicamento = "Vitamina D", fotoUrl = null, intervalHours = 24,
            totalDoses = 0, startAtMillis = now + hour, reasonId = "r1", reasonNombre = "Déficit",
            openEnded = true, nowMillis = now,
        )
        doses.forEach { DoseAlarmScheduler.scheduleDose(context, schedule.id, it, "Vitamina D", 0) }
        val taker = DoseTaker(schedules, FakeMedicationHistoryRepository(), now = { now + day }) { s, added ->
            added.forEach { DoseAlarmScheduler.scheduleDose(context, s.id, it, s.nombreMedicamento, 0) }
        }
        taker.markTaken(schedule, doses.first())
        DoseAlarmScheduler.cancelDose(context, schedule.id, doses.first().id)
        assertEquals(7, alarms.scheduledAlarms.size)
        return schedule.id
    }

    @Test
    fun `closing from the detail cancels the alarms of every skipped dose`() = runTest(dispatcher) {
        val scheduleId = openEndedWithRefill()
        val viewModel = ScheduleDetailViewModel(
            schedules,
            DoseTaker(schedules, FakeMedicationHistoryRepository()),
            SavedStateHandle(mapOf("scheduleId" to scheduleId)),
        )

        viewModel.closeSchedule(context)
        advanceUntilIdle()

        assertTrue(schedules.dosesSnapshot(scheduleId).none { it.status == DoseStatus.PENDING })
        assertTrue(alarms.scheduledAlarms.isEmpty())
    }

}
