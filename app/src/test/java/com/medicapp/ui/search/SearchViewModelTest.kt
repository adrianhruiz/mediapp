package com.medicapp.ui.search

import com.medicapp.data.remote.FakeCimaApiService
import com.medicapp.data.remote.dto.MedicamentoDto
import com.medicapp.data.remote.dto.MedicamentosResponseDto
import com.medicapp.data.repository.MedicationSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModelTest {

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
    fun `starts in Idle state before typing anything`() = runTest(dispatcher) {
        val viewModel = SearchViewModel(MedicationSearchRepository(FakeCimaApiService()))

        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `typing a query shows the results once the debounce elapses`() = runTest(dispatcher) {
        val dto = MedicamentoDto(nregistro = "1", nombre = "XAZAL")
        val response = MedicamentosResponseDto(totalFilas = 1, pagina = 1, tamanioPagina = 25, resultados = listOf(dto))
        val viewModel = SearchViewModel(MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response)))
        val collectorJob = launch { viewModel.uiState.collect {} }

        viewModel.onQueryChanged("xazal")
        advanceTimeBy(400)
        runCurrent()

        val state = viewModel.uiState.value
        assertTrue(state is SearchUiState.Success)
        assertEquals("XAZAL", (state as SearchUiState.Success).results.single().nombre)
        collectorJob.cancel()
    }

    @Test
    fun `no results are reported as Empty state`() = runTest(dispatcher) {
        val response = MedicamentosResponseDto(totalFilas = 0, pagina = 1, tamanioPagina = 25, resultados = emptyList())
        val viewModel = SearchViewModel(MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response)))
        val collectorJob = launch { viewModel.uiState.collect {} }

        viewModel.onQueryChanged("noexiste")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(SearchUiState.Empty, viewModel.uiState.value)
        collectorJob.cancel()
    }

    @Test
    fun `clearing the query back to blank returns to Idle`() = runTest(dispatcher) {
        val response = MedicamentosResponseDto(totalFilas = 0, pagina = 1, tamanioPagina = 25, resultados = emptyList())
        val viewModel = SearchViewModel(MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response)))
        val collectorJob = launch { viewModel.uiState.collect {} }

        viewModel.onQueryChanged("xazal")
        advanceTimeBy(400)
        runCurrent()
        viewModel.onQueryChanged("")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
        collectorJob.cancel()
    }
}
