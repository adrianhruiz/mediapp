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
