package com.medicapp.ui.schedule

import android.app.AlarmManager
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.medicapp.data.repository.FakeReasonRepository
import com.medicapp.data.repository.FakeScheduleRepository
import com.medicapp.model.DoseStatus
import com.medicapp.notifications.DoseAlarmScheduler
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class EditScheduleViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val hour = 3_600_000L
    private val start = 1_000_000_000_000L
    private val now = start + 10 * hour

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))

    private val scheduleRepository = FakeScheduleRepository()
    private val reasonRepository = FakeReasonRepository()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    /** Pauta de 4 tomas cada 8 h con la primera hecha y sus alarmas puestas, como al crearla. */
    private suspend fun scheduleWithFirstDoseTaken(): String {
        val created = scheduleRepository.createSchedule(
            nregistro = "1",
            nombreMedicamento = "Ibuprofeno",
            fotoUrl = null,
            intervalHours = 8,
            totalDoses = 4,
            startAtMillis = start,
            reasonId = "r1",
            reasonNombre = "Dolor",
        )
        created.doses.forEach { DoseAlarmScheduler.scheduleDose(context, created.schedule.id, it, "Ibuprofeno", 4) }
        val first = created.doses.first()
        scheduleRepository.markDoseTaken(created.schedule.id, first.id, "h1")
        DoseAlarmScheduler.cancelDose(context, created.schedule.id, first.id)
        return created.schedule.id
    }

    private fun TestScope.editViewModel(scheduleId: String): CreateScheduleViewModel {
        val viewModel = CreateScheduleViewModel(
            scheduleRepository,
            reasonRepository,
            SavedStateHandle(mapOf("scheduleId" to scheduleId)),
            now = { now },
        )
        advanceUntilIdle()
        return viewModel
    }

    @Test
    fun `editing loads the schedule values and the next pending dose time`() = runTest(dispatcher) {
        val viewModel = editViewModel(scheduleWithFirstDoseTaken())

        val state = viewModel.uiState.value
        assertTrue(state.isEditing)
        assertEquals("Ibuprofeno", state.nombreMedicamento)
        assertEquals("8", state.intervalHoursInput)
        assertEquals("4", state.totalDosesInput)
        assertEquals("Dolor", state.reasonInput)
        assertEquals(1, state.keptDoseCount)
        // La toma 2 (start + 8 h) ya pasó sin marcar; la próxima por delante es la 3.
        assertEquals(start + 16 * hour, state.startAtMillis)
        assertFalse(state.hasDuplicateActiveSchedule)
    }

    @Test
    fun `saving an edit regenerates pending doses and moves their alarms`() = runTest(dispatcher) {
        val scheduleId = scheduleWithFirstDoseTaken()
        val viewModel = editViewModel(scheduleId)
        val next = now + hour

        viewModel.onIntervalHoursChanged("12")
        viewModel.onTotalDosesChanged("3")
        viewModel.onStartAtChanged(next)
        viewModel.onReasonInputChanged("Fiebre")
        viewModel.save(context)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        val schedule = scheduleRepository.getSchedule(scheduleId)!!
        assertEquals(12, schedule.intervalHours)
        assertEquals(3, schedule.totalDoses)
        assertEquals("Fiebre", schedule.reasonNombre)
        val doses = scheduleRepository.dosesSnapshot(scheduleId)
        assertEquals(listOf(DoseStatus.TAKEN, DoseStatus.PENDING, DoseStatus.PENDING), doses.map { it.status })
        assertEquals(listOf(start, next, next + 12 * hour), doses.map { it.scheduledAtMillis })
        assertEquals(listOf(next, next + 12 * hour), alarms.scheduledAlarms.map { it.triggerAtTime }.sorted())
    }

    @Test
    fun `a total that leaves no dose to take is not saved`() = runTest(dispatcher) {
        val scheduleId = scheduleWithFirstDoseTaken()
        val viewModel = editViewModel(scheduleId)

        viewModel.onTotalDosesChanged("1")
        viewModel.save(context)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.totalBelowTaken)
        assertFalse(viewModel.uiState.value.isSaved)
        assertEquals(4, scheduleRepository.getSchedule(scheduleId)!!.totalDoses)
        assertEquals(3, alarms.scheduledAlarms.size)
    }

    @Test
    fun `creating a schedule still creates it with its alarms`() = runTest(dispatcher) {
        val viewModel = CreateScheduleViewModel(
            scheduleRepository,
            reasonRepository,
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Ibuprofeno")),
        )
        advanceUntilIdle()

        viewModel.onIntervalHoursChanged("12")
        viewModel.onTotalDosesChanged("2")
        viewModel.onStartAtChanged(now)
        viewModel.onReasonInputChanged("Dolor")
        viewModel.save(context)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertFalse(viewModel.uiState.value.isEditing)
        val schedule = scheduleRepository.schedules.single()
        assertEquals(2, schedule.totalDoses)
        assertEquals(listOf(now, now + 12 * hour), alarms.scheduledAlarms.map { it.triggerAtTime }.sorted())
    }
}
