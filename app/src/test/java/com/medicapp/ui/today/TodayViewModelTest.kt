package com.medicapp.ui.today

import com.medicapp.data.repository.FakeMedicationHistoryRepository
import com.medicapp.data.repository.FakeScheduleRepository
import com.medicapp.domain.DoseTaker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
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
import java.time.ZoneOffset

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val hour = 3_600_000L
    private val day = 24 * hour

    // 22 de octubre de 2026 a las 00:00 UTC.
    private val today = 1_792_627_200_000L

    private val schedules = FakeScheduleRepository()
    private val doseTaker = DoseTaker(schedules, FakeMedicationHistoryRepository())

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private suspend fun schedule(nregistro: String, startAt: Long, intervalHours: Int, total: Int) =
        schedules.createSchedule(nregistro, "MEDICAMENTO $nregistro", null, intervalHours, total, startAt, "r", "Motivo")

    @Test
    fun `shows only today's doses, in order, with the earliest pending one as next`() = runTest(dispatcher) {
        schedule("1", startAt = today + 8 * hour, intervalHours = 6, total = 3)
        schedule("2", startAt = today - day + 9 * hour, intervalHours = 24, total = 1)
        val clock = MutableStateFlow(today + 10 * hour)
        val viewModel = TodayViewModel(schedules, doseTaker, ZoneOffset.UTC, clock)
        backgroundScope.launch { viewModel.uiState.collect {} }

        advanceUntilIdle()

        val plan = viewModel.uiState.value.plan
        assertEquals(listOf(8L, 14L, 20L), plan.doses.map { (it.dose.scheduledAtMillis - today) / hour })
        assertEquals(today + 8 * hour, plan.next?.dose?.scheduledAtMillis)
        assertTrue(plan.doses.first().overdue)
        assertFalse(plan.doses[1].overdue)
    }

    @Test
    fun `taking the next dose moves next to the following one`() = runTest(dispatcher) {
        val (created, doses) = schedule("1", startAt = today + 8 * hour, intervalHours = 6, total = 3)
        val viewModel = TodayViewModel(schedules, doseTaker, ZoneOffset.UTC, MutableStateFlow(today + 10 * hour))
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        doseTaker.markTaken(created, doses.first())
        advanceUntilIdle()

        assertEquals(today + 14 * hour, viewModel.uiState.value.plan.next?.dose?.scheduledAtMillis)
    }

    @Test
    fun `a new day loads that day's doses`() = runTest(dispatcher) {
        schedule("1", startAt = today + 8 * hour, intervalHours = 24, total = 2)
        val clock = MutableStateFlow(today + 10 * hour)
        val viewModel = TodayViewModel(schedules, doseTaker, ZoneOffset.UTC, clock)
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        clock.value = today + day + 1 * hour
        advanceUntilIdle()

        assertEquals(today + day + 8 * hour, viewModel.uiState.value.plan.doses.single().dose.scheduledAtMillis)
    }

    @Test
    fun `with every dose taken there is no next one`() = runTest(dispatcher) {
        val (created, doses) = schedule("1", startAt = today + 8 * hour, intervalHours = 24, total = 1)
        doseTaker.markTaken(created, doses.single())
        val viewModel = TodayViewModel(schedules, doseTaker, ZoneOffset.UTC, MutableStateFlow(today + 10 * hour))
        backgroundScope.launch { viewModel.uiState.collect {} }

        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.plan.allDone)
    }
}
