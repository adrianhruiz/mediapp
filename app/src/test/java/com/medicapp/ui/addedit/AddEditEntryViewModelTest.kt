package com.medicapp.ui.addedit

import androidx.lifecycle.SavedStateHandle
import com.medicapp.data.repository.FakeMedicationHistoryRepository
import com.medicapp.data.repository.FakeReasonRepository
import com.medicapp.model.Reason
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
class AddEditEntryViewModelTest {

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
    fun `saving a new entry reuses an existing reason instead of duplicating it`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        val reasonRepository = FakeReasonRepository(
            initialReasons = listOf(Reason(id = "r1", nombre = "Dolor de cabeza")),
        )
        val viewModel = AddEditEntryViewModel(
            historyRepository,
            reasonRepository,
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Ibuprofeno")),
        )
        advanceUntilIdle()

        viewModel.onReasonInputChanged("Dolor de cabeza")
        viewModel.save()
        advanceUntilIdle()

        val entry = historyRepository.entries.single()
        assertEquals("r1", entry.reasonId)
        assertEquals("Ibuprofeno", entry.nombreMedicamento)
        assertEquals(1, reasonRepository.reasons.size)
        assertTrue(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `saving with a brand new reason creates it`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        val reasonRepository = FakeReasonRepository()
        val viewModel = AddEditEntryViewModel(
            historyRepository,
            reasonRepository,
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Ibuprofeno")),
        )
        advanceUntilIdle()

        viewModel.onReasonInputChanged("Migraña")
        viewModel.save()
        advanceUntilIdle()

        assertEquals(1, reasonRepository.reasons.size)
        assertEquals("Migraña", historyRepository.entries.single().reasonNombre)
    }

    @Test
    fun `suggestions filter reasons as the user types`() = runTest(dispatcher) {
        val reasonRepository = FakeReasonRepository(
            initialReasons = listOf(
                Reason(id = "r1", nombre = "Dolor de cabeza"),
                Reason(id = "r2", nombre = "Alergia"),
            ),
        )
        val viewModel = AddEditEntryViewModel(
            FakeMedicationHistoryRepository(),
            reasonRepository,
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Ibuprofeno")),
        )
        advanceUntilIdle()

        viewModel.onReasonInputChanged("dolor")

        assertEquals(1, viewModel.uiState.value.suggestions.size)
        assertEquals("Dolor de cabeza", viewModel.uiState.value.suggestions.first().nombre)
    }

    @Test
    fun `does not save when the reason is blank`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        val viewModel = AddEditEntryViewModel(
            historyRepository,
            FakeReasonRepository(),
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Ibuprofeno")),
        )
        advanceUntilIdle()

        viewModel.save()
        advanceUntilIdle()

        assertTrue(historyRepository.entries.isEmpty())
        assertFalse(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `the same medication with the same reason cannot be added twice`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        historyRepository.add(
            nregistro = "1",
            nombreMedicamento = "Ibuprofeno",
            reasonId = "r1",
            reasonNombre = "Dolor de cabeza",
            dosisSnapshot = null,
            fotoUrl = null,
        )
        val viewModel = AddEditEntryViewModel(
            historyRepository,
            FakeReasonRepository(initialReasons = listOf(Reason(id = "r1", nombre = "Dolor de cabeza"))),
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Ibuprofeno")),
        )
        advanceUntilIdle()

        viewModel.onReasonInputChanged("dolor de cabeza")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.isDuplicate)
        assertFalse(viewModel.uiState.value.canSave)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(1, historyRepository.entries.size)
        assertFalse(viewModel.uiState.value.isSaved)
    }

    @Test
    fun `the same medication with a different reason can be added`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        historyRepository.add(
            nregistro = "1",
            nombreMedicamento = "Ibuprofeno",
            reasonId = "r1",
            reasonNombre = "Dolor de cabeza",
            dosisSnapshot = null,
            fotoUrl = null,
        )
        val viewModel = AddEditEntryViewModel(
            historyRepository,
            FakeReasonRepository(),
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Ibuprofeno")),
        )
        advanceUntilIdle()

        viewModel.onReasonInputChanged("Fiebre")
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.isDuplicate)

        viewModel.save()
        advanceUntilIdle()

        assertEquals(2, historyRepository.entries.size)
    }

    @Test
    fun `saving twice in a row only records one entry`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        val viewModel = AddEditEntryViewModel(
            historyRepository,
            FakeReasonRepository(initialReasons = listOf(Reason(id = "r1", nombre = "Dolor de cabeza"))),
            SavedStateHandle(mapOf("nregistro" to "1", "nombre" to "Ibuprofeno")),
        )
        advanceUntilIdle()

        viewModel.onReasonInputChanged("Dolor de cabeza")
        viewModel.save()
        viewModel.save()
        advanceUntilIdle()

        assertEquals(1, historyRepository.entries.size)
    }

    @Test
    fun `editing an entry cannot reuse a reason already registered for that medication`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        val entryId = historyRepository.add(
            nregistro = "1",
            nombreMedicamento = "Ibuprofeno",
            reasonId = "r1",
            reasonNombre = "Dolor de cabeza",
            dosisSnapshot = null,
            fotoUrl = null,
        )
        historyRepository.add(
            nregistro = "1",
            nombreMedicamento = "Ibuprofeno",
            reasonId = "r2",
            reasonNombre = "Fiebre",
            dosisSnapshot = null,
            fotoUrl = null,
        )
        val viewModel = AddEditEntryViewModel(
            historyRepository,
            FakeReasonRepository(),
            SavedStateHandle(mapOf("entryId" to entryId)),
        )
        advanceUntilIdle()

        viewModel.onReasonInputChanged("Fiebre")
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isDuplicate)
        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `editing an entry keeping its own reason is not a duplicate`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        val entryId = historyRepository.add(
            nregistro = "1",
            nombreMedicamento = "Ibuprofeno",
            reasonId = "r1",
            reasonNombre = "Dolor de cabeza",
            dosisSnapshot = null,
            fotoUrl = null,
        )
        val viewModel = AddEditEntryViewModel(
            historyRepository,
            FakeReasonRepository(),
            SavedStateHandle(mapOf("entryId" to entryId)),
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isDuplicate)
        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun `editing an existing entry loads its reason and updates it on save`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        val reasonRepository = FakeReasonRepository()
        val entryId = historyRepository.add(
            nregistro = "1",
            nombreMedicamento = "Ibuprofeno",
            reasonId = "r1",
            reasonNombre = "Dolor de cabeza",
            dosisSnapshot = null,
            fotoUrl = null,
        )
        val viewModel = AddEditEntryViewModel(
            historyRepository,
            reasonRepository,
            SavedStateHandle(mapOf("entryId" to entryId)),
        )
        advanceUntilIdle()
        assertEquals("Dolor de cabeza", viewModel.uiState.value.reasonInput)

        viewModel.onReasonInputChanged("Migraña")
        viewModel.save()
        advanceUntilIdle()

        assertEquals("Migraña", historyRepository.entries.single().reasonNombre)
    }
}
