package com.medicapp.ui.search

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medicapp.data.remote.FakeCimaApiService
import com.medicapp.data.remote.dto.AtcDto
import com.medicapp.data.remote.dto.MedicamentoDetailDto
import com.medicapp.data.remote.dto.PrincipioActivoDto
import com.medicapp.data.repository.FakeAllergyRepository
import com.medicapp.data.repository.FakeMedicationHistoryRepository
import com.medicapp.data.repository.MedicationCatalogRepository
import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import com.medicapp.model.TakenEntry
import com.medicapp.ui.theme.MedicAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MedicationDetailScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val detailDto = MedicamentoDetailDto(
        nregistro = "77758",
        nombre = "PARACETAMOL ABAMED 1G",
        principiosActivos = listOf(PrincipioActivoDto(id = 745, nombre = "PARACETAMOL")),
        atcs = listOf(AtcDto(codigo = "N02BE01", nombre = "Paracetamol")),
    )

    private fun setContent(
        allergies: List<Allergy>,
        history: FakeMedicationHistoryRepository = FakeMedicationHistoryRepository(),
    ) {
        val viewModel = MedicationDetailViewModel(
            MedicationCatalogRepository(FakeCimaApiService(medicamentoDetailResponse = detailDto)),
            FakeAllergyRepository(allergies),
            history,
            SavedStateHandle(mapOf("nregistro" to "77758")),
        )
        composeRule.setContent {
            MedicAppTheme {
                MedicationDetailScreen(
                    onAddToHistory = {},
                    onCreateReminder = {},
                    onBack = {},
                    viewModel = viewModel,
                )
            }
        }
    }

    @Test
    fun addToHistoryIsEnabledWhenThereIsNoAllergy() {
        setContent(emptyList())

        composeRule.onNodeWithText("Añadir al historial").assertIsEnabled()
    }

    @Test
    fun addToHistoryIsDisabledWhenAnIngredientAllergyMatches() {
        setContent(
            listOf(
                Allergy(
                    id = "1",
                    tipo = AllergyType.PRINCIPIO_ACTIVO,
                    principioActivoId = 745,
                    principioActivoNombre = "PARACETAMOL",
                ),
            ),
        )

        composeRule.onNodeWithText("Desrecomendado").assertExists()
        composeRule.onNodeWithText("Añadir al historial").assertIsNotEnabled()
    }

    @Test
    fun addToHistoryIsDisabledWhenAFamilyAllergyMatches() {
        setContent(
            listOf(Allergy(id = "1", tipo = AllergyType.FAMILIA, atcCodigo = "N02B", atcNombre = "Analgésicos")),
        )

        composeRule.onNodeWithText("Añadir al historial").assertIsNotEnabled()
    }

    @Test
    fun aMedicationAlreadyInTheHistoryShowsTheAddedBadge() {
        val history = FakeMedicationHistoryRepository(
            listOf(
                TakenEntry(
                    id = "entry-1",
                    nregistro = "77758",
                    nombreMedicamento = "PARACETAMOL ABAMED 1G",
                    reasonId = "r1",
                    reasonNombre = "Fiebre",
                    timestampMillis = 0L,
                    dosisSnapshot = null,
                    fotoUrl = null,
                ),
            ),
        )
        setContent(allergies = emptyList(), history = history)

        composeRule.onNodeWithText("Ya en tu historial").assertExists()
        composeRule.onNodeWithText("Motivos registrados: Fiebre").assertExists()
    }

    @Test
    fun aMedicationOutsideTheHistoryDoesNotShowTheAddedBadge() {
        setContent(emptyList())

        composeRule.onNodeWithText("Ya en tu historial").assertDoesNotExist()
    }

    @Test
    fun markingTheIngredientAsAllergicDisablesAddToHistoryOnTheFly() {
        setContent(emptyList())
        composeRule.onNodeWithText("Añadir al historial").assertIsEnabled()

        composeRule.onNodeWithText("PARACETAMOL").performClick()

        composeRule.onNodeWithText("Añadir al historial").assertIsNotEnabled()
    }
}
