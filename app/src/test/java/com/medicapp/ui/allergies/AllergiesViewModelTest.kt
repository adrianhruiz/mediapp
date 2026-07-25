package com.medicapp.ui.allergies

import com.medicapp.data.remote.FakeCimaApiService
import com.medicapp.data.remote.dto.MaestraItemDto
import com.medicapp.data.remote.dto.MaestrasResponseDto
import com.medicapp.data.repository.FakeAllergyRepository
import com.medicapp.data.repository.MedicationCatalogRepository
import com.medicapp.model.AtcCode
import com.medicapp.model.PrincipioActivo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
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
class AllergiesViewModelTest {

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
    fun `searching for a family surfaces ATC results`() = runTest(dispatcher) {
        val response = MaestrasResponseDto(
            totalFilas = 1,
            pagina = 1,
            tamanioPagina = 200,
            resultados = listOf(MaestraItemDto(codigo = "J01C", nombre = "Penicilinas")),
        )
        val viewModel = AllergiesViewModel(
            FakeAllergyRepository(),
            MedicationCatalogRepository(FakeCimaApiService(maestrasResponse = response)),
        )
        val collectorJob = launch { viewModel.searchState.collect {} }

        viewModel.onSearchModeChanged(AllergySearchMode.FAMILIA)
        viewModel.onQueryChanged("penicilina")
        advanceTimeBy(400)
        runCurrent()

        val state = viewModel.searchState.value
        assertTrue(state is CatalogSearchUiState.FamilyResults)
        assertEquals("J01C", (state as CatalogSearchUiState.FamilyResults).items.first().codigo)
        collectorJob.cancel()
    }

    @Test
    fun `adding a family allergy shows up in the allergies list`() = runTest(dispatcher) {
        val allergyRepository = FakeAllergyRepository()
        val viewModel = AllergiesViewModel(allergyRepository, MedicationCatalogRepository(FakeCimaApiService()))
        val collectorJob = launch { viewModel.allergies.collect {} }
        runCurrent()

        viewModel.addFamilyAllergy(AtcCode(codigo = "J01C", nombre = "Penicilinas", nivel = 3))
        advanceUntilIdle()

        assertEquals(1, viewModel.allergies.value.size)
        assertEquals("J01C", viewModel.allergies.value.first().atcCodigo)
        collectorJob.cancel()
    }

    @Test
    fun `adding the same family allergy twice does not duplicate it`() = runTest(dispatcher) {
        val allergyRepository = FakeAllergyRepository()
        val viewModel = AllergiesViewModel(allergyRepository, MedicationCatalogRepository(FakeCimaApiService()))
        val collectorJob = launch { viewModel.allergies.collect {} }
        runCurrent()

        val atc = AtcCode(codigo = "J01C", nombre = "Penicilinas", nivel = 3)
        viewModel.addFamilyAllergy(atc)
        advanceUntilIdle()
        viewModel.addFamilyAllergy(atc)
        advanceUntilIdle()

        assertEquals(1, viewModel.allergies.value.size)
        collectorJob.cancel()
    }

    @Test
    fun `adding the same ingredient allergy twice does not duplicate it`() = runTest(dispatcher) {
        val allergyRepository = FakeAllergyRepository()
        val viewModel = AllergiesViewModel(allergyRepository, MedicationCatalogRepository(FakeCimaApiService()))
        val collectorJob = launch { viewModel.allergies.collect {} }
        runCurrent()

        val principioActivo = PrincipioActivo(id = 42, nombre = "Ibuprofeno")
        viewModel.addIngredientAllergy(principioActivo)
        advanceUntilIdle()
        viewModel.addIngredientAllergy(principioActivo)
        advanceUntilIdle()

        assertEquals(1, viewModel.allergies.value.size)
        collectorJob.cancel()
    }

    @Test
    fun `adding all search results marks every family found for the keyword`() = runTest(dispatcher) {
        val response = MaestrasResponseDto(
            totalFilas = 2,
            pagina = 1,
            tamanioPagina = 200,
            resultados = listOf(
                MaestraItemDto(codigo = "J01C", nombre = "Penicilinas"),
                MaestraItemDto(codigo = "J01CA", nombre = "Penicilinas de amplio espectro"),
            ),
        )
        val viewModel = AllergiesViewModel(
            FakeAllergyRepository(),
            MedicationCatalogRepository(FakeCimaApiService(maestrasResponse = response)),
        )
        val searchJob = launch { viewModel.searchState.collect {} }
        val allergiesJob = launch { viewModel.allergies.collect {} }

        viewModel.onSearchModeChanged(AllergySearchMode.FAMILIA)
        viewModel.onQueryChanged("penicilina")
        advanceTimeBy(400)
        runCurrent()

        viewModel.addAllSearchResults()
        advanceUntilIdle()

        assertEquals(
            listOf("J01C", "J01CA"),
            viewModel.allergies.value.map { it.atcCodigo },
        )
        searchJob.cancel()
        allergiesJob.cancel()
    }

    @Test
    fun `adding all search results skips the ones already marked`() = runTest(dispatcher) {
        val response = MaestrasResponseDto(
            totalFilas = 2,
            pagina = 1,
            tamanioPagina = 200,
            resultados = listOf(
                MaestraItemDto(codigo = "J01C", nombre = "Penicilinas"),
                MaestraItemDto(codigo = "J01CA", nombre = "Penicilinas de amplio espectro"),
            ),
        )
        val viewModel = AllergiesViewModel(
            FakeAllergyRepository(),
            MedicationCatalogRepository(FakeCimaApiService(maestrasResponse = response)),
        )
        val searchJob = launch { viewModel.searchState.collect {} }
        val allergiesJob = launch { viewModel.allergies.collect {} }

        viewModel.addFamilyAllergy(AtcCode(codigo = "J01C", nombre = "Penicilinas", nivel = 3))
        advanceUntilIdle()

        viewModel.onSearchModeChanged(AllergySearchMode.FAMILIA)
        viewModel.onQueryChanged("penicilina")
        advanceTimeBy(400)
        runCurrent()

        viewModel.addAllSearchResults()
        advanceUntilIdle()

        assertEquals(
            listOf("J01C", "J01CA"),
            viewModel.allergies.value.map { it.atcCodigo },
        )
        searchJob.cancel()
        allergiesJob.cancel()
    }

    @Test
    fun `adding all search results takes a single write`() = runTest(dispatcher) {
        val response = MaestrasResponseDto(
            totalFilas = 3,
            pagina = 1,
            tamanioPagina = 200,
            resultados = listOf(
                MaestraItemDto(codigo = "J01C", nombre = "Penicilinas"),
                MaestraItemDto(codigo = "J01CA", nombre = "Penicilinas de amplio espectro"),
                MaestraItemDto(codigo = "J01CE", nombre = "Penicilinas sensibles"),
            ),
        )
        val allergyRepository = FakeAllergyRepository()
        val viewModel = AllergiesViewModel(
            allergyRepository,
            MedicationCatalogRepository(FakeCimaApiService(maestrasResponse = response)),
        )
        val searchJob = launch { viewModel.searchState.collect {} }
        val allergiesJob = launch { viewModel.allergies.collect {} }

        viewModel.onSearchModeChanged(AllergySearchMode.FAMILIA)
        viewModel.onQueryChanged("penicilina")
        advanceTimeBy(400)
        runCurrent()

        viewModel.addAllSearchResults()
        advanceUntilIdle()

        assertEquals(3, viewModel.allergies.value.size)
        assertEquals("las tres van en el mismo lote", 1, allergyRepository.writeCount)
        searchJob.cancel()
        allergiesJob.cancel()
    }

    @Test
    fun `marking the same allergy twice does not duplicate it`() = runTest(dispatcher) {
        val allergyRepository = FakeAllergyRepository()
        val viewModel = AllergiesViewModel(
            allergyRepository,
            MedicationCatalogRepository(FakeCimaApiService()),
        )
        val allergiesJob = launch { viewModel.allergies.collect {} }

        allergyRepository.addPrincipioActivo(214, "IBUPROFENO")
        allergyRepository.addPrincipioActivo(214, "IBUPROFENO")
        advanceUntilIdle()

        assertEquals(1, viewModel.allergies.value.size)
        allergiesJob.cancel()
    }

    @Test
    fun `addableResultCount only counts results that are not already marked`() = runTest(dispatcher) {
        val response = MaestrasResponseDto(
            totalFilas = 2,
            pagina = 1,
            tamanioPagina = 200,
            resultados = listOf(
                MaestraItemDto(codigo = "J01C", nombre = "Penicilinas"),
                MaestraItemDto(codigo = "J01CA", nombre = "Penicilinas de amplio espectro"),
            ),
        )
        val viewModel = AllergiesViewModel(
            FakeAllergyRepository(),
            MedicationCatalogRepository(FakeCimaApiService(maestrasResponse = response)),
        )
        val searchJob = launch { viewModel.searchState.collect {} }
        val allergiesJob = launch { viewModel.allergies.collect {} }
        val countJob = launch { viewModel.addableResultCount.collect {} }

        viewModel.onSearchModeChanged(AllergySearchMode.FAMILIA)
        viewModel.onQueryChanged("penicilina")
        advanceTimeBy(400)
        runCurrent()

        assertEquals(2, viewModel.addableResultCount.value)

        viewModel.addFamilyAllergy(AtcCode(codigo = "J01C", nombre = "Penicilinas", nivel = 3))
        advanceUntilIdle()

        assertEquals(1, viewModel.addableResultCount.value)

        searchJob.cancel()
        allergiesJob.cancel()
        countJob.cancel()
    }

    @Test
    fun `adding all search results marks every ingredient found for the keyword`() = runTest(dispatcher) {
        val response = MaestrasResponseDto(
            totalFilas = 2,
            pagina = 1,
            tamanioPagina = 200,
            resultados = listOf(
                MaestraItemDto(id = 1, nombre = "Ibuprofeno"),
                MaestraItemDto(id = 2, nombre = "Ibuprofeno arginina"),
            ),
        )
        val viewModel = AllergiesViewModel(
            FakeAllergyRepository(),
            MedicationCatalogRepository(FakeCimaApiService(maestrasResponse = response)),
        )
        val searchJob = launch { viewModel.searchState.collect {} }
        val allergiesJob = launch { viewModel.allergies.collect {} }

        viewModel.onQueryChanged("ibuprofeno")
        advanceTimeBy(400)
        runCurrent()

        viewModel.addAllSearchResults()
        advanceUntilIdle()

        assertEquals(
            listOf("Ibuprofeno", "Ibuprofeno arginina"),
            viewModel.allergies.value.map { it.principioActivoNombre },
        )
        searchJob.cancel()
        allergiesJob.cancel()
    }

    @Test
    fun `deleting an allergy removes it from the list`() = runTest(dispatcher) {
        val allergyRepository = FakeAllergyRepository()
        val viewModel = AllergiesViewModel(allergyRepository, MedicationCatalogRepository(FakeCimaApiService()))
        val collectorJob = launch { viewModel.allergies.collect {} }
        viewModel.addFamilyAllergy(AtcCode(codigo = "J01C", nombre = "Penicilinas", nivel = 3))
        advanceUntilIdle()
        val added = viewModel.allergies.value.first()

        viewModel.delete(added)
        advanceUntilIdle()

        assertTrue(viewModel.allergies.value.isEmpty())
        collectorJob.cancel()
    }
}
