package com.medicapp.ui.allergies

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.width
import com.medicapp.model.AtcCode
import com.medicapp.model.PrincipioActivo
import com.medicapp.ui.components.AddedChip
import org.junit.Assert.assertTrue
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
                AllergiesScreen(contentPadding = PaddingValues(), onOpenReport = {}, viewModel = viewModel)
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

    /**
     * Con un nombre largo el chip quedaba aplastado y su texto se partía letra a letra.
     * Se compara con un chip suelto, que ocupa su ancho natural.
     */
    private fun setRow(row: @Composable () -> Unit) {
        composeRule.setContent {
            MedicAppTheme {
                Column {
                    AddedChip("Ya añadida", Modifier.testTag("reference"))
                    Box(Modifier.width(360.dp).testTag("row")) { row() }
                }
            }
        }
    }

    private fun assertAddedChipIsNotSqueezed() {
        val natural = composeRule.onNodeWithTag("reference").getUnclippedBoundsInRoot().width
        val inRow = composeRule.onNode(hasText("Ya añadida") and hasAnyAncestor(hasTestTag("row")))
            .getUnclippedBoundsInRoot().width
        assertTrue("El chip mide $inRow de ancho en vez de $natural", inRow >= natural - 1.dp)
    }

    @Test
    fun aLongIngredientNameDoesNotSqueezeTheAddedChip() {
        setRow {
            IngredientResultRow(
                item = PrincipioActivo(id = 111, nombre = "METAMIZOL MAGNESICO HEXAHIDRATO"),
                alreadyAdded = true,
                onAdd = {},
            )
        }

        assertAddedChipIsNotSqueezed()
    }

    @Test
    fun aLongFamilyNameDoesNotSqueezeTheAddedChip() {
        setRow {
            FamilyResultRow(
                item = AtcCode(codigo = "M02AA", nombre = "Preparados con antiinflamatorios no esteroideos para uso topico", nivel = 4),
                alreadyAdded = true,
                onAdd = {},
            )
        }

        assertAddedChipIsNotSqueezed()
    }
}
