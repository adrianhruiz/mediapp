package com.medicapp.ui.history

import com.medicapp.data.repository.FakeMedicationHistoryRepository
import com.medicapp.data.repository.FakeReasonRepository
import com.medicapp.model.Reason
import com.medicapp.model.TakenEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun entry(id: String, nombre: String, reasonId: String, reasonNombre: String) = TakenEntry(
        id = id,
        nregistro = "1",
        nombreMedicamento = nombre,
        reasonId = reasonId,
        reasonNombre = reasonNombre,
        timestampMillis = 0L,
        dosisSnapshot = null,
        fotoUrl = null,
    )

    @Test
    fun `hasAnyEntries reflects whether the history is empty`() = runTest(dispatcher) {
        val viewModel = HistoryViewModel(FakeMedicationHistoryRepository(), FakeReasonRepository())
        val collectorJob = launch { viewModel.hasAnyEntries.collect {} }
        runCurrent()

        assertFalse(viewModel.hasAnyEntries.value)
        collectorJob.cancel()
    }

    @Test
    fun `filters entries by the selected reason`() = runTest(dispatcher) {
        val entries = listOf(
            entry("1", "Ibuprofeno", "r1", "Dolor de cabeza"),
            entry("2", "Paracetamol", "r2", "Fiebre"),
        )
        val viewModel = HistoryViewModel(FakeMedicationHistoryRepository(entries), FakeReasonRepository())
        val collectorJob = launch { viewModel.entries.collect {} }
        runCurrent()

        viewModel.onReasonFilterChanged("r1")
        runCurrent()

        assertEquals(listOf("Ibuprofeno"), viewModel.entries.value.map { it.nombreMedicamento })
        collectorJob.cancel()
    }

    @Test
    fun `filters entries by free text across medication name and reason`() = runTest(dispatcher) {
        val entries = listOf(
            entry("1", "Ibuprofeno", "r1", "Dolor de cabeza"),
            entry("2", "Paracetamol", "r2", "Fiebre"),
        )
        val viewModel = HistoryViewModel(FakeMedicationHistoryRepository(entries), FakeReasonRepository())
        val collectorJob = launch { viewModel.entries.collect {} }
        runCurrent()

        viewModel.onQueryChanged("fiebre")
        runCurrent()

        assertEquals(listOf("Paracetamol"), viewModel.entries.value.map { it.nombreMedicamento })
        collectorJob.cancel()
    }

    @Test
    fun `deleting an entry removes it from the list`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository(
            listOf(entry("1", "Ibuprofeno", "r1", "Dolor de cabeza")),
        )
        val viewModel = HistoryViewModel(historyRepository, FakeReasonRepository())
        val collectorJob = launch { viewModel.entries.collect {} }
        runCurrent()

        viewModel.delete(historyRepository.entries.single())
        advanceUntilIdle()

        assertTrue(viewModel.entries.value.isEmpty())
        collectorJob.cancel()
    }

    @Test
    fun `loadMore grows the loaded window until hasMore is false`() = runTest(dispatcher) {
        val entries = (1..25).map { entry("$it", "Medicamento $it", "r1", "Dolor de cabeza") }
        val historyRepository = FakeMedicationHistoryRepository(entries)
        val viewModel = HistoryViewModel(historyRepository, FakeReasonRepository())
        val entriesJob = launch { viewModel.entries.collect {} }
        val hasMoreJob = launch { viewModel.hasMore.collect {} }
        runCurrent()

        assertEquals(20, viewModel.entries.value.size)
        assertTrue(viewModel.hasMore.value)

        viewModel.loadMore()
        runCurrent()

        assertEquals(25, viewModel.entries.value.size)
        assertFalse(viewModel.hasMore.value)

        entriesJob.cancel()
        hasMoreJob.cancel()
    }

    @Test
    fun `exposes the available reasons for the filter dropdown`() = runTest(dispatcher) {
        val reasonRepository = FakeReasonRepository(
            initialReasons = listOf(Reason(id = "r1", nombre = "Dolor de cabeza")),
        )
        val viewModel = HistoryViewModel(FakeMedicationHistoryRepository(), reasonRepository)
        val collectorJob = launch { viewModel.reasons.collect {} }
        runCurrent()

        assertEquals(listOf("Dolor de cabeza"), viewModel.reasons.value.map { it.nombre })
        collectorJob.cancel()
    }
}
