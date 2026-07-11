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
}
