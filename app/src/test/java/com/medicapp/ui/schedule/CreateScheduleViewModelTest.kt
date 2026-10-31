package com.medicapp.ui.schedule

import androidx.lifecycle.SavedStateHandle
import com.medicapp.data.repository.FakeReasonRepository
import com.medicapp.data.repository.FakeScheduleRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
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

@OptIn(ExperimentalCoroutinesApi::class)
class CreateScheduleViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `no duplicate warning when there is no existing schedule for the medication`() = runTest(dispatcher) {
        val viewModel = CreateScheduleViewModel(
            FakeScheduleRepository(),
            FakeReasonRepository(),
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Ibuprofeno")),
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasDuplicateActiveSchedule)
    }

    @Test
    fun `duplicate warning appears when an active schedule already exists for the medication`() = runTest(dispatcher) {
        val scheduleRepository = FakeScheduleRepository()
        scheduleRepository.createSchedule(
            nregistro = "1",
            nombreMedicamento = "Ibuprofeno",
            fotoUrl = null,
            intervalHours = 24,
            totalDoses = 1,
            startAtMillis = 0L,
            reasonId = "r1",
            reasonNombre = "Dolor",
        )
        val viewModel = CreateScheduleViewModel(
            scheduleRepository,
            FakeReasonRepository(),
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Ibuprofeno")),
        )
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.hasDuplicateActiveSchedule)
    }

    @Test
    fun `no duplicate warning when the existing schedule for the medication is closed`() = runTest(dispatcher) {
        val scheduleRepository = FakeScheduleRepository()
        val created = scheduleRepository.createSchedule(
            nregistro = "1",
            nombreMedicamento = "Ibuprofeno",
            fotoUrl = null,
            intervalHours = 24,
            totalDoses = 1,
            startAtMillis = 0L,
            reasonId = "r1",
            reasonNombre = "Dolor",
        )
        scheduleRepository.closeSchedule(created.schedule.id)
        val viewModel = CreateScheduleViewModel(
            scheduleRepository,
            FakeReasonRepository(),
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Ibuprofeno")),
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasDuplicateActiveSchedule)
    }

    @Test
    fun `duplicate warning does not trigger for a different medication`() = runTest(dispatcher) {
        val scheduleRepository = FakeScheduleRepository()
        scheduleRepository.createSchedule(
            nregistro = "1",
            nombreMedicamento = "Ibuprofeno",
            fotoUrl = null,
            intervalHours = 24,
            totalDoses = 1,
            startAtMillis = 0L,
            reasonId = "r1",
            reasonNombre = "Dolor",
        )
        val viewModel = CreateScheduleViewModel(
            scheduleRepository,
            FakeReasonRepository(),
            SavedStateHandle(mapOf("nregistro" to "2", "nombre" to "Paracetamol")),
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.hasDuplicateActiveSchedule)
    }

    private fun viewModel(scheduleRepository: FakeScheduleRepository = FakeScheduleRepository()) =
        CreateScheduleViewModel(
            scheduleRepository,
            FakeReasonRepository(),
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Ibuprofeno")),
        )

    @Test
    fun `total doses come from the days and the preset interval`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onIntervalPresetSelected(8)
        repeat(6) { viewModel.incrementDays() }

        assertEquals(7, viewModel.uiState.value.days)
        assertEquals(21, viewModel.uiState.value.totalDoses)
    }

    @Test
    fun `days never go below one`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.decrementDays()

        assertEquals(1, viewModel.uiState.value.days)
    }

    @Test
    fun `choosing Otra keeps the interval editable and uses it for the total`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onCustomIntervalSelected()
        viewModel.onIntervalHoursChanged("10")
        viewModel.incrementDays()

        assertTrue(viewModel.uiState.value.isCustomInterval)
        assertEquals(5, viewModel.uiState.value.totalDoses)
    }

    @Test
    fun `a preset clears Otra`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        viewModel.onCustomIntervalSelected()
        viewModel.onIntervalPresetSelected(12)

        assertFalse(viewModel.uiState.value.isCustomInterval)
        assertEquals("12", viewModel.uiState.value.intervalHoursInput)
    }

    @Test
    fun `cannot save without a reason or without a valid interval`() = runTest(dispatcher) {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canSave)
        viewModel.onReasonInputChanged("Dolor")
        assertTrue(viewModel.uiState.value.canSave)
        viewModel.onCustomIntervalSelected()
        viewModel.onIntervalHoursChanged("")
        assertFalse(viewModel.uiState.value.canSave)
        viewModel.onIntervalHoursChanged("0")
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `cannot save while another active schedule exists for the medication`() = runTest(dispatcher) {
        val scheduleRepository = FakeScheduleRepository()
        scheduleRepository.createSchedule(
            nregistro = "1",
            nombreMedicamento = "Ibuprofeno",
            fotoUrl = null,
            intervalHours = 24,
            totalDoses = 1,
            startAtMillis = 0L,
            reasonId = "r1",
            reasonNombre = "Dolor",
        )
        val viewModel = viewModel(scheduleRepository)
        advanceUntilIdle()

        viewModel.onReasonInputChanged("Dolor")

        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `tapping a reason chip fills the reason`() = runTest(dispatcher) {
        val viewModel = CreateScheduleViewModel(
            FakeScheduleRepository(),
            FakeReasonRepository(initialReasons = listOf(com.medicapp.model.Reason(id = "r1", nombre = "Alergia"))),
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Ibuprofeno")),
        )
        advanceUntilIdle()

        viewModel.onReasonSelected(viewModel.uiState.value.reasons.single())

        assertEquals("Alergia", viewModel.uiState.value.reasonInput)
    }
}
