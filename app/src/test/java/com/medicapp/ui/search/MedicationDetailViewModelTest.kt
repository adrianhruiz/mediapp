package com.medicapp.ui.search

import androidx.lifecycle.SavedStateHandle
import com.medicapp.data.remote.FakeCimaApiService
import com.medicapp.data.remote.dto.AtcDto
import com.medicapp.data.remote.dto.MedicamentoDetailDto
import com.medicapp.data.remote.dto.PrincipioActivoDto
import com.medicapp.data.repository.FakeAllergyRepository
import com.medicapp.data.repository.MedicationCatalogRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MedicationDetailViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    private val detailDto = MedicamentoDetailDto(
        nregistro = "77758",
        nombre = "PARACETAMOL ABAMED 1G",
        principiosActivos = listOf(PrincipioActivoDto(id = 745, nombre = "PARACETAMOL")),
        atcs = listOf(AtcDto(codigo = "N02BE01", nombre = "Paracetamol")),
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(allergyRepository: FakeAllergyRepository) = MedicationDetailViewModel(
        MedicationCatalogRepository(FakeCimaApiService(medicamentoDetailResponse = detailDto)),
        allergyRepository,
        SavedStateHandle(mapOf("nregistro" to "77758")),
    )

    @Test
    fun `loads the medication detail and starts with no allergy matches`() = runTest(dispatcher) {
        val viewModel = createViewModel(FakeAllergyRepository())
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("PARACETAMOL ABAMED 1G", state.medication?.nombre)
        assertTrue(state.matches.isEmpty())
    }

    @Test
    fun `toggling a matching ingredient allergy surfaces a warning`() = runTest(dispatcher) {
        val viewModel = createViewModel(FakeAllergyRepository())
        advanceUntilIdle()

        viewModel.toggleIngredientAllergy(viewModel.uiState.value.medication!!.principiosActivos.first())
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.matches.size)
    }

    @Test
    fun `toggling the same ingredient allergy twice removes the warning again`() = runTest(dispatcher) {
        val viewModel = createViewModel(FakeAllergyRepository())
        advanceUntilIdle()
        val ingredient = viewModel.uiState.value.medication!!.principiosActivos.first()

        viewModel.toggleIngredientAllergy(ingredient)
        advanceUntilIdle()
        viewModel.toggleIngredientAllergy(ingredient)
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.matches.isEmpty())
    }

    @Test
    fun `a pre-existing family allergy already shows a warning on load`() = runTest(dispatcher) {
        val allergyRepository = FakeAllergyRepository()
        val viewModel = createViewModel(allergyRepository)
        advanceUntilIdle()

        viewModel.toggleFamiliaAllergy(viewModel.uiState.value.medication!!.atcs.first())
        advanceUntilIdle()

        assertEquals(1, viewModel.uiState.value.matches.size)
        assertEquals(1, allergyRepository.allergies.size)
    }
}
