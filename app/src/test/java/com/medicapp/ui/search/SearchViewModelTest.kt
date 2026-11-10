package com.medicapp.ui.search

import com.medicapp.data.remote.FakeCimaApiService
import com.medicapp.data.remote.dto.MedicamentoDto
import com.medicapp.data.remote.dto.MedicamentosResponseDto
import com.medicapp.data.repository.FakeMedicationHistoryRepository
import com.medicapp.data.repository.FakeScheduleRepository
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
import com.medicapp.data.remote.CimaUnavailableException
import com.medicapp.model.SavedMedication
import kotlinx.coroutines.CoroutineScope
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
        val viewModel = SearchViewModel(
            MedicationSearchRepository(FakeCimaApiService()),
            FakeScheduleRepository(),
            FakeMedicationHistoryRepository(),
        )

        assertEquals(SearchUiState.Idle, viewModel.uiState.value)
    }

    @Test
    fun `typing a query shows the results once the debounce elapses`() = runTest(dispatcher) {
        val dto = MedicamentoDto(nregistro = "1", nombre = "XAZAL")
        val response = MedicamentosResponseDto(totalFilas = 1, pagina = 1, tamanioPagina = 25, resultados = listOf(dto))
        val viewModel = SearchViewModel(
            MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response)),
            FakeScheduleRepository(),
            FakeMedicationHistoryRepository(),
        )
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
        val viewModel = SearchViewModel(
            MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response)),
            FakeScheduleRepository(),
            FakeMedicationHistoryRepository(),
        )
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
        val viewModel = SearchViewModel(
            MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response)),
            FakeScheduleRepository(),
            FakeMedicationHistoryRepository(),
        )
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

    @Test
    fun `medications with an active schedule are marked as saved in the results`() = runTest(dispatcher) {
        val dto = MedicamentoDto(nregistro = "1", nombre = "XAZAL")
        val response = MedicamentosResponseDto(totalFilas = 1, pagina = 1, tamanioPagina = 25, resultados = listOf(dto))
        val scheduleRepository = FakeScheduleRepository()
        scheduleRepository.createSchedule(
            nregistro = "1",
            nombreMedicamento = "XAZAL",
            fotoUrl = null,
            intervalHours = 24,
            totalDoses = 1,
            startAtMillis = 0L,
            reasonId = "r1",
            reasonNombre = "Alergia",
        )
        val viewModel = SearchViewModel(
            MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response)),
            scheduleRepository,
            FakeMedicationHistoryRepository(),
        )
        val collectorJob = launch { viewModel.uiState.collect {} }
        val savedJob = launch { viewModel.savedNregistros.collect {} }

        viewModel.onQueryChanged("xazal")
        advanceTimeBy(400)
        runCurrent()

        assertTrue(viewModel.uiState.value is SearchUiState.Success)
        assertTrue(viewModel.savedNregistros.value.contains("1"))
        collectorJob.cancel()
        savedJob.cancel()
    }

    @Test
    fun `medications without any schedule are not marked as saved`() = runTest(dispatcher) {
        val dto = MedicamentoDto(nregistro = "1", nombre = "XAZAL")
        val response = MedicamentosResponseDto(totalFilas = 1, pagina = 1, tamanioPagina = 25, resultados = listOf(dto))
        val viewModel = SearchViewModel(
            MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response)),
            FakeScheduleRepository(),
            FakeMedicationHistoryRepository(),
        )
        val collectorJob = launch { viewModel.uiState.collect {} }
        val savedJob = launch { viewModel.savedNregistros.collect {} }

        viewModel.onQueryChanged("xazal")
        advanceTimeBy(400)
        runCurrent()

        assertTrue(viewModel.uiState.value is SearchUiState.Success)
        assertTrue(viewModel.savedNregistros.value.isEmpty())
        collectorJob.cancel()
        savedJob.cancel()
    }

    @Test
    fun `medications already in the history are flagged in the results`() = runTest(dispatcher) {
        val dto = MedicamentoDto(nregistro = "1", nombre = "XAZAL")
        val response = MedicamentosResponseDto(totalFilas = 1, pagina = 1, tamanioPagina = 25, resultados = listOf(dto))
        val historyRepository = FakeMedicationHistoryRepository()
        historyRepository.add(
            nregistro = "1",
            nombreMedicamento = "XAZAL",
            reasonId = "r1",
            reasonNombre = "Alergia",
            dosisSnapshot = null,
            fotoUrl = null,
        )
        val viewModel = SearchViewModel(
            MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response)),
            FakeScheduleRepository(),
            historyRepository,
        )
        val collectorJob = launch { viewModel.uiState.collect {} }
        val historyJob = launch { viewModel.historyNregistros.collect {} }

        viewModel.onQueryChanged("xazal")
        advanceTimeBy(400)
        runCurrent()

        assertTrue(viewModel.uiState.value is SearchUiState.Success)
        assertTrue(viewModel.historyNregistros.value.contains("1"))
        collectorJob.cancel()
        historyJob.cancel()
    }

    @Test
    fun `medications not in the history are not flagged`() = runTest(dispatcher) {
        val dto = MedicamentoDto(nregistro = "1", nombre = "XAZAL")
        val response = MedicamentosResponseDto(totalFilas = 1, pagina = 1, tamanioPagina = 25, resultados = listOf(dto))
        val viewModel = SearchViewModel(
            MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response)),
            FakeScheduleRepository(),
            FakeMedicationHistoryRepository(),
        )
        val collectorJob = launch { viewModel.uiState.collect {} }
        val historyJob = launch { viewModel.historyNregistros.collect {} }

        viewModel.onQueryChanged("xazal")
        advanceTimeBy(400)
        runCurrent()

        assertTrue(viewModel.uiState.value is SearchUiState.Success)
        assertTrue(viewModel.historyNregistros.value.isEmpty())
        collectorJob.cancel()
        historyJob.cancel()
    }

    @Test
    fun `medications with only a closed schedule are not marked as saved`() = runTest(dispatcher) {
        val dto = MedicamentoDto(nregistro = "1", nombre = "XAZAL")
        val response = MedicamentosResponseDto(totalFilas = 1, pagina = 1, tamanioPagina = 25, resultados = listOf(dto))
        val scheduleRepository = FakeScheduleRepository()
        val created = scheduleRepository.createSchedule(
            nregistro = "1",
            nombreMedicamento = "XAZAL",
            fotoUrl = null,
            intervalHours = 24,
            totalDoses = 1,
            startAtMillis = 0L,
            reasonId = "r1",
            reasonNombre = "Alergia",
        )
        scheduleRepository.closeSchedule(created.schedule.id)
        val viewModel = SearchViewModel(
            MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response)),
            scheduleRepository,
            FakeMedicationHistoryRepository(),
        )
        val collectorJob = launch { viewModel.uiState.collect {} }
        val savedJob = launch { viewModel.savedNregistros.collect {} }

        viewModel.onQueryChanged("xazal")
        advanceTimeBy(400)
        runCurrent()

        assertTrue(viewModel.uiState.value is SearchUiState.Success)
        assertTrue(viewModel.savedNregistros.value.isEmpty())
        collectorJob.cancel()
        savedJob.cancel()
    }

    private fun response(vararg medications: Pair<String, String>) = MedicamentosResponseDto(
        totalFilas = medications.size,
        pagina = 1,
        tamanioPagina = 25,
        resultados = medications.map { (nregistro, nombre) -> MedicamentoDto(nregistro = nregistro, nombre = nombre) },
    )

    private suspend fun FakeMedicationHistoryRepository.addEntry(nregistro: String, nombre: String) {
        add(nregistro, nombre, reasonId = "r1", reasonNombre = "Alergia", dosisSnapshot = null, fotoUrl = null)
    }

    private fun CoroutineScope.collectAll(viewModel: SearchViewModel) = listOf(
        launch { viewModel.uiState.collect {} },
        launch { viewModel.savedResults.collect {} },
        launch { viewModel.savedNregistros.collect {} },
        launch { viewModel.historyNregistros.collect {} },
    )

    @Test
    fun `saved medications come first and CIMA results skip them`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        historyRepository.addEntry("1", "XAZAL 5 mg")
        val viewModel = SearchViewModel(
            MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response("1" to "XAZAL 5 mg", "2" to "XAZAL 10 mg"))),
            FakeScheduleRepository(),
            historyRepository,
        )
        val jobs = collectAll(viewModel)

        viewModel.onQueryChanged("xazal")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(listOf("1"), viewModel.savedResults.value.map { it.nregistro })
        val state = viewModel.uiState.value as SearchUiState.Success
        assertEquals(listOf("2"), state.results.map { it.nregistro })
        // El total de CIMA se rebaja con lo que ya sale arriba, para que "Mostrando X de Y" cuadre.
        assertEquals(1, state.totalFilas)
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `a medication in history and in an active schedule is shown once`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        historyRepository.addEntry("propio-pomada-de-arnica", "Pomada de Árnica")
        val scheduleRepository = FakeScheduleRepository()
        scheduleRepository.createSchedule(
            nregistro = "propio-pomada-de-arnica",
            nombreMedicamento = "Pomada de Árnica",
            fotoUrl = null,
            intervalHours = 24,
            totalDoses = 1,
            startAtMillis = 0L,
            reasonId = "r1",
            reasonNombre = "Golpe",
        )
        val viewModel = SearchViewModel(
            MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response())),
            scheduleRepository,
            historyRepository,
        )
        val jobs = collectAll(viewModel)

        viewModel.onQueryChanged("ARNICA pomada")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(
            listOf(SavedMedication("propio-pomada-de-arnica", "Pomada de Árnica", null)),
            viewModel.savedResults.value,
        )
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `saved medications are visible while CIMA is still loading`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        historyRepository.addEntry("1", "XAZAL 5 mg")
        val viewModel = SearchViewModel(
            MedicationSearchRepository(
                FakeCimaApiService(medicamentosResponse = response("2" to "XAZAL 10 mg"), medicamentosDelayMillis = 5_000),
            ),
            FakeScheduleRepository(),
            historyRepository,
        )
        val jobs = collectAll(viewModel)

        viewModel.onQueryChanged("xazal")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(SearchUiState.Loading, viewModel.uiState.value)
        assertEquals(listOf("1"), viewModel.savedResults.value.map { it.nregistro })
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `saved medications stay when CIMA fails, with its error below`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        historyRepository.addEntry("1", "XAZAL 5 mg")
        val viewModel = SearchViewModel(
            MedicationSearchRepository(FakeCimaApiService(medicamentosError = CimaUnavailableException())),
            FakeScheduleRepository(),
            historyRepository,
        )
        val jobs = collectAll(viewModel)

        viewModel.onQueryChanged("xazal")
        advanceTimeBy(400)
        runCurrent()

        assertTrue(viewModel.uiState.value is SearchUiState.Error)
        assertEquals(listOf("1"), viewModel.savedResults.value.map { it.nregistro })
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `saved medications stay when CIMA finds nothing`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        historyRepository.addEntry("propio-pomada-de-arnica", "Pomada de Árnica")
        val viewModel = SearchViewModel(
            MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response())),
            FakeScheduleRepository(),
            historyRepository,
        )
        val jobs = collectAll(viewModel)

        viewModel.onQueryChanged("arnica")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(SearchUiState.Empty, viewModel.uiState.value)
        assertEquals(listOf("propio-pomada-de-arnica"), viewModel.savedResults.value.map { it.nregistro })
        jobs.forEach { it.cancel() }
    }

    @Test
    fun `without saved matches loading and errors look as before`() = runTest(dispatcher) {
        val historyRepository = FakeMedicationHistoryRepository()
        historyRepository.addEntry("9", "DALSY")
        val viewModel = SearchViewModel(
            MedicationSearchRepository(
                FakeCimaApiService(medicamentosError = CimaUnavailableException(), medicamentosDelayMillis = 5_000),
            ),
            FakeScheduleRepository(),
            historyRepository,
        )
        val jobs = collectAll(viewModel)

        viewModel.onQueryChanged("xazal")
        advanceTimeBy(400)
        runCurrent()
        assertEquals(SearchUiState.Loading, viewModel.uiState.value)
        assertTrue(viewModel.savedResults.value.isEmpty())

        advanceTimeBy(5_000)
        runCurrent()
        assertEquals(
            SearchUiState.Error(
                "El buscador de medicamentos no está disponible ahora mismo. Inténtalo de nuevo en unos minutos.",
            ),
            viewModel.uiState.value,
        )
        jobs.forEach { it.cancel() }
    }
}
