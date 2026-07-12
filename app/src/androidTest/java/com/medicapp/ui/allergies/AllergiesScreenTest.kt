package com.medicapp.ui.allergies

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
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

    @Test
    fun anAllergyAlreadyMarkedIsFlaggedInTheSearchResults() {
        setContent(
            listOf(Allergy(id = "1", tipo = AllergyType.FAMILIA, atcCodigo = "J01C", atcNombre = "Penicilinas")),
        )

        composeRule.onNodeWithText("Familia (ATC)").performClick()
        composeRule.onNodeWithText("Buscar familia (ej. Penicilina)").performTextInput("penicilina")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithText("Ya añadida").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Ya añadida").assertExists()
    }

    @Test
    fun anAllergyNotMarkedYetOffersTheAddAction() {
        setContent(emptyList())

        composeRule.onNodeWithText("Familia (ATC)").performClick()
        composeRule.onNodeWithText("Buscar familia (ej. Penicilina)").performTextInput("penicilina")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodesWithContentDescription("Añadir alergia").fetchSemanticsNodes().isNotEmpty()
        }

        composeRule.onNodeWithText("Ya añadida").assertDoesNotExist()
    }
}
