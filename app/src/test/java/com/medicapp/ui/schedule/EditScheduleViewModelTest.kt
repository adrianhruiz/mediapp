package com.medicapp.ui.schedule

import android.app.AlarmManager
import android.app.Application
import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import com.medicapp.data.repository.FakeReasonRepository
import com.medicapp.data.repository.FakeScheduleRepository
import com.medicapp.domain.DoseInterval
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
        assertEquals("8", state.intervalAmountInput)
        assertEquals(DoseInterval.Unit.HOURS, state.intervalUnit)
        // 4 tomas cada 8 h caben en 2 días, pero el total cargado se respeta tal cual.
        assertEquals(2, state.days)
        assertEquals(4, state.totalDoses)
        assertFalse(state.isCustomInterval)
        assertEquals("Dolor", state.reasonInput)
        assertEquals(1, state.keptDoseCount)
        // La toma 2 (start + 8 h) ya pasó sin marcar; la próxima por delante es la 3.
        assertEquals(start + 16 * hour, state.startAtMillis)
        assertFalse(state.hasDuplicateActiveSchedule)
    }

    @Test
    fun `editing a weekly schedule loads the interval as 1 week`() = runTest(dispatcher) {
        val created = scheduleRepository.createSchedule(
            nregistro = "2",
            nombreMedicamento = "Vitamina D",
            fotoUrl = null,
            intervalHours = 168,
            totalDoses = 4,
            startAtMillis = start,
            reasonId = "r2",
            reasonNombre = "Déficit",
        )
        val viewModel = editViewModel(created.schedule.id)

        val state = viewModel.uiState.value
        assertEquals("1", state.intervalAmountInput)
        assertEquals(DoseInterval.Unit.WEEKS, state.intervalUnit)
        assertEquals(168, state.intervalHours)
        // Una semana no está en «8 h · 12 h · 24 h»: el formulario se abre por «Otra».
        assertTrue(state.isCustomInterval)
        assertEquals(28, state.days)
        assertEquals(4, state.totalDoses)
    }

    @Test
    fun `editing only the reason keeps the stored total of doses`() = runTest(dispatcher) {
        val scheduleId = scheduleWithFirstDoseTaken()
        val viewModel = editViewModel(scheduleId)

        viewModel.onReasonInputChanged("Fiebre")
        viewModel.save(context)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        assertEquals(4, scheduleRepository.getSchedule(scheduleId)!!.totalDoses)
    }

    @Test
    fun `touching the duration recomputes the total from the days`() = runTest(dispatcher) {
        val viewModel = editViewModel(scheduleWithFirstDoseTaken())

        viewModel.incrementDays()

        // 3 días cada 8 h: ya no manda el total cargado, sino la cuenta.
        assertEquals(3, viewModel.uiState.value.days)
        assertEquals(9, viewModel.uiState.value.totalDoses)
    }

    @Test
    fun `saving an edit regenerates pending doses and moves their alarms`() = runTest(dispatcher) {
        val scheduleId = scheduleWithFirstDoseTaken()
        val viewModel = editViewModel(scheduleId)
        val next = now + hour

        // Cada 12 h durante los 2 días cargados: 4 tomas en total, de las que 1 ya está hecha.
        viewModel.onIntervalPresetSelected(12)
        viewModel.onStartAtChanged(next)
        viewModel.onReasonInputChanged("Fiebre")
        viewModel.save(context)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        val schedule = scheduleRepository.getSchedule(scheduleId)!!
        assertEquals(12, schedule.intervalHours)
        assertEquals(4, schedule.totalDoses)
        assertEquals("Fiebre", schedule.reasonNombre)
        val doses = scheduleRepository.dosesSnapshot(scheduleId)
        assertEquals(listOf(DoseStatus.TAKEN, DoseStatus.PENDING, DoseStatus.PENDING, DoseStatus.PENDING), doses.map { it.status })
        assertEquals(listOf(start, next, next + 12 * hour, next + 24 * hour), doses.map { it.scheduledAtMillis })
        assertEquals(listOf(next, next + 12 * hour, next + 24 * hour), alarms.scheduledAlarms.map { it.triggerAtTime }.sorted())
    }

    @Test
    fun `a total that leaves no dose to take is not saved`() = runTest(dispatcher) {
        val scheduleId = scheduleWithFirstDoseTaken()
        val viewModel = editViewModel(scheduleId)

        // Una toma al día durante un día: 1 toma, y esa ya está hecha.
        viewModel.onIntervalPresetSelected(24)
        viewModel.decrementDays()
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

        viewModel.onIntervalPresetSelected(12)
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

    private val day = 24 * hour

    @Test
    fun `an open-ended schedule is saved without a total and with a week of doses and alarms`() = runTest(dispatcher) {
        val viewModel = CreateScheduleViewModel(
            scheduleRepository,
            reasonRepository,
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Vitamina D")),
            now = { now },
        )
        advanceUntilIdle()

        viewModel.onIntervalPresetSelected(24)
        viewModel.onOpenEndedChanged(true)
        viewModel.onStartAtChanged(now)
        viewModel.onReasonInputChanged("Déficit")
        viewModel.save(context)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        val schedule = scheduleRepository.schedules.single()
        assertTrue(schedule.openEnded)
        assertEquals(0, schedule.totalDoses)
        val expected = (0 until 7).map { now + it * day }
        assertEquals(expected, scheduleRepository.dosesSnapshot(schedule.id).map { it.scheduledAtMillis })
        assertEquals(expected, alarms.scheduledAlarms.map { it.triggerAtTime }.sorted())
    }

    @Test
    fun `editing an open-ended schedule keeps done doses and regenerates the window with the new interval`() = runTest(dispatcher) {
        val created = scheduleRepository.createSchedule(
            nregistro = "1", nombreMedicamento = "Vitamina D", fotoUrl = null, intervalHours = 24,
            totalDoses = 0, startAtMillis = start, reasonId = "r1", reasonNombre = "Déficit",
            openEnded = true, nowMillis = start,
        )
        scheduleRepository.markDoseTaken(created.schedule.id, created.doses.first().id, "h1")
        val viewModel = editViewModel(created.schedule.id)
        assertTrue(viewModel.uiState.value.openEnded)
        val next = now + hour

        viewModel.onIntervalPresetSelected(48)
        viewModel.onStartAtChanged(next)
        viewModel.save(context)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSaved)
        val schedule = scheduleRepository.getSchedule(created.schedule.id)!!
        assertTrue(schedule.openEnded)
        assertEquals(48, schedule.intervalHours)
        val doses = scheduleRepository.dosesSnapshot(created.schedule.id)
        assertEquals(DoseStatus.TAKEN, doses.first().status)
        assertEquals(listOf(start) + (0..3).map { next + it * 2 * day }, doses.map { it.scheduledAtMillis })
        assertEquals((1..5).toList(), doses.map { it.doseNumber })
    }

    @Test
    fun `an open-ended schedule can be switched to a fixed total`() = runTest(dispatcher) {
        val created = scheduleRepository.createSchedule(
            nregistro = "1", nombreMedicamento = "Vitamina D", fotoUrl = null, intervalHours = 24,
            totalDoses = 0, startAtMillis = start, reasonId = "r1", reasonNombre = "Déficit",
            openEnded = true, nowMillis = start,
        )
        scheduleRepository.markDoseTaken(created.schedule.id, created.doses.first().id, "h1")
        val viewModel = editViewModel(created.schedule.id)

        // Una toma al día durante 3 días: 3 tomas, de las que la primera ya está hecha.
        viewModel.onOpenEndedChanged(false)
        viewModel.incrementDays()
        viewModel.incrementDays()
        viewModel.onStartAtChanged(now + hour)
        viewModel.save(context)
        advanceUntilIdle()

        val schedule = scheduleRepository.getSchedule(created.schedule.id)!!
        assertFalse(schedule.openEnded)
        assertEquals(3, schedule.totalDoses)
        assertEquals(3, scheduleRepository.dosesSnapshot(created.schedule.id).size)
    }

    @Test
    fun `a fixed schedule can be switched to open-ended`() = runTest(dispatcher) {
        val scheduleId = scheduleWithFirstDoseTaken()
        val viewModel = editViewModel(scheduleId)
        assertFalse(viewModel.uiState.value.openEnded)

        viewModel.onOpenEndedChanged(true)
        viewModel.onStartAtChanged(now + hour)
        viewModel.save(context)
        advanceUntilIdle()

        val schedule = scheduleRepository.getSchedule(scheduleId)!!
        assertTrue(schedule.openEnded)
        assertEquals(0, schedule.totalDoses)
        val doses = scheduleRepository.dosesSnapshot(scheduleId)
        assertEquals(DoseStatus.TAKEN, doses.first().status)
        // Cada 8 h durante 7 días desde la próxima toma: 21 nuevas tras la hecha.
        assertEquals(22, doses.size)
    }
}
