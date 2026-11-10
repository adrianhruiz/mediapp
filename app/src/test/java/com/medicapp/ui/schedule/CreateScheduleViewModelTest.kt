package com.medicapp.ui.schedule

import androidx.lifecycle.SavedStateHandle
import com.medicapp.data.repository.FakeReasonRepository
import com.medicapp.data.repository.FakeScheduleRepository
import com.medicapp.domain.DoseInterval
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
import org.junit.Assert.assertNull
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

    @Test
    fun `selecting the weekly preset stores 168 hours as 1 week`() = runTest(dispatcher) {
        val viewModel = newViewModel()

        viewModel.onIntervalPresetSelected(168)

        val state = viewModel.uiState.value
        assertEquals("1", state.intervalAmountInput)
        assertEquals(DoseInterval.Unit.WEEKS, state.intervalUnit)
        assertEquals(168, state.intervalHours)
    }

    @Test
    fun `typing 2 and choosing weeks saves a 336 hour interval`() = runTest(dispatcher) {
        val viewModel = newViewModel()

        viewModel.onIntervalAmountChanged("2")
        viewModel.onIntervalUnitChanged(DoseInterval.Unit.WEEKS)

        assertEquals(336, viewModel.uiState.value.intervalHours)
    }

    @Test
    fun `a blank or zero amount leaves the interval undefined`() = runTest(dispatcher) {
        val viewModel = newViewModel()

        viewModel.onIntervalAmountChanged("")
        assertNull(viewModel.uiState.value.intervalHours)

        viewModel.onIntervalAmountChanged("0")
        assertNull(viewModel.uiState.value.intervalHours)
    }

    @Test
    fun `the amount field only accepts digits`() = runTest(dispatcher) {
        val viewModel = newViewModel()

        viewModel.onIntervalAmountChanged("2a")

        assertEquals("1", viewModel.uiState.value.intervalAmountInput)
        assertEquals(24, viewModel.uiState.value.intervalHours)
    }

    private fun newViewModel(): CreateScheduleViewModel {
        val viewModel = CreateScheduleViewModel(
            FakeScheduleRepository(),
            FakeReasonRepository(),
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Ibuprofeno")),
        )
        dispatcher.scheduler.advanceUntilIdle()
        return viewModel
    }
}
