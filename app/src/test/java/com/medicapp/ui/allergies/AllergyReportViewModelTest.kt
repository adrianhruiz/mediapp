package com.medicapp.ui.allergies

import com.medicapp.data.repository.FakeAllergyRepository
import com.medicapp.data.repository.FakeProfileRepository
import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import com.medicapp.model.PatientProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class AllergyReportViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private val allergies = listOf(
        Allergy(id = "1", tipo = AllergyType.PRINCIPIO_ACTIVO, principioActivoId = 1, principioActivoNombre = "METAMIZOL MAGNESICO"),
        Allergy(id = "2", tipo = AllergyType.FAMILIA, atcCodigo = "J01C", atcNombre = "PENICILINAS"),
        Allergy(id = "3", tipo = AllergyType.PRINCIPIO_ACTIVO, principioActivoId = 3, principioActivoNombre = "BENCILPENICILINA"),
    )

    @Test
    fun `the report groups the allergies and starts in Spanish without a profile`() = runTest(dispatcher) {
        val viewModel = AllergyReportViewModel(FakeAllergyRepository(allergies), FakeProfileRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(ReportLanguage.ES, state.language)
        assertNull(state.profile)
        assertEquals(listOf("2"), state.groups.families.map { it.id })
        assertEquals(listOf("3", "1"), state.groups.ingredients.map { it.id })
    }

    @Test
    fun `switching to English changes the report language`() = runTest(dispatcher) {
        val viewModel = AllergyReportViewModel(FakeAllergyRepository(allergies), FakeProfileRepository())
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.onLanguageSelected(ReportLanguage.EN)
        advanceUntilIdle()

        assertEquals(ReportLanguage.EN, viewModel.uiState.value.language)
    }

    @Test
    fun `saving the patient data stores a trimmed name and the birth date`() = runTest(dispatcher) {
        val profileRepository = FakeProfileRepository()
        val viewModel = AllergyReportViewModel(FakeAllergyRepository(allergies), profileRepository)
        backgroundScope.launch { viewModel.uiState.collect {} }

        viewModel.saveProfile("  Ana García  ", LocalDate.of(1985, 3, 12))
        advanceUntilIdle()

        val expected = PatientProfile(nombre = "Ana García", fechaNacimiento = LocalDate.of(1985, 3, 12))
        assertEquals(expected, profileRepository.observe().first())
        assertEquals(expected, viewModel.uiState.value.profile)
    }

    @Test
    fun `a blank name is not saved`() = runTest(dispatcher) {
        val profileRepository = FakeProfileRepository()
        val viewModel = AllergyReportViewModel(FakeAllergyRepository(allergies), profileRepository)

        viewModel.saveProfile("   ", LocalDate.of(1985, 3, 12))
        advanceUntilIdle()

        assertNull(profileRepository.observe().first())
    }

    @Test
    fun `birth dates read the same in both languages with the month spelled out`() {
        val date = LocalDate.of(1985, 3, 12)

        assertEquals("12 mar 1985", ReportText.of(ReportLanguage.ES).formatDate(date))
        assertEquals("12 Mar 1985", ReportText.of(ReportLanguage.EN).formatDate(date))
    }
}
