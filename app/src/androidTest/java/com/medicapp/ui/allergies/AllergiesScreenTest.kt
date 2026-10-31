package com.medicapp.ui.allergies

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medicapp.data.remote.FakeCimaApiService
import com.medicapp.data.remote.dto.MaestraItemDto
import com.medicapp.data.remote.dto.MaestrasResponseDto
import com.medicapp.data.repository.FakeAllergyRepository
import com.medicapp.data.repository.MedicationCatalogRepository
import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import com.medicapp.ui.theme.MedicAppTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AllergiesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val familyResults = MaestrasResponseDto(
        totalFilas = 1,
        pagina = 1,
        tamanioPagina = 200,
        resultados = listOf(MaestraItemDto(codigo = "J01C", nombre = "Penicilinas")),
    )

    private fun setContent(allergies: List<Allergy>) {
        val viewModel = AllergiesViewModel(
            FakeAllergyRepository(allergies),
            MedicationCatalogRepository(FakeCimaApiService(maestrasResponse = familyResults)),
        )
        composeRule.setContent {
            MedicAppTheme {
                AllergiesScreen(contentPadding = PaddingValues(), viewModel = viewModel)
            }
        }
    }

    /** El buscador vive en la hoja «Añadir alergia»; no hay que elegir el tipo antes. */
    private fun openAddSheetAndSearch(text: String) {
        composeRule.onNodeWithText("Añadir alergia").performClick()
        composeRule.onNode(hasSetTextAction()).performTextInput(text)
    }

    @Test
    fun deletingTakesTwoStepsFromEditMode() {
        val repository = FakeAllergyRepository(
            listOf(Allergy(id = "1", tipo = AllergyType.FAMILIA, atcCodigo = "J01C", atcNombre = "Penicilinas")),
        )
        val viewModel = AllergiesViewModel(repository, MedicationCatalogRepository(FakeCimaApiService()))
        composeRule.setContent {
            MedicAppTheme { AllergiesScreen(contentPadding = PaddingValues(), viewModel = viewModel) }
        }

        composeRule.onNodeWithText("FAMILIAS ATC · 1").assertExists()
        composeRule.onNodeWithContentDescription("Quitar alergia").assertDoesNotExist()

        composeRule.onNodeWithText("Editar").performClick()
        composeRule.onNodeWithContentDescription("Quitar alergia").performClick()
        composeRule.onNodeWithText("Eliminar").performClick()

        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("J01C").fetchSemanticsNodes().isEmpty()
        }
    }

    @Test
    fun anAllergyAlreadyMarkedIsFlaggedInTheSearchResults() {
        setContent(
            listOf(Allergy(id = "1", tipo = AllergyType.FAMILIA, atcCodigo = "J01C", atcNombre = "Penicilinas")),
        )

        openAddSheetAndSearch("penicilina")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("Ya añadida").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Ya añadida").assertExists()
    }

    @Test
    fun anAllergyNotMarkedYetOffersTheAddAction() {
        setContent(emptyList())

        openAddSheetAndSearch("penicilina")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("Añadir alergia").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithContentDescription("Ya añadida").assertDoesNotExist()
    }
}
