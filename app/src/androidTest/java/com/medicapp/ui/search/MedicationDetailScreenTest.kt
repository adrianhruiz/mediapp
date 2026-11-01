package com.medicapp.ui.search

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medicapp.data.remote.FakeCimaApiService
import com.medicapp.data.remote.dto.AtcDto
import com.medicapp.data.remote.dto.FotoDto
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

    private val enlargePhoto = SemanticsMatcher("click to enlarge the photo") {
        it.config.getOrNull(SemanticsActions.OnClick)?.label == "Ampliar foto"
    }

    private fun setContent(
        allergies: List<Allergy>,
        history: FakeMedicationHistoryRepository = FakeMedicationHistoryRepository(),
        detailDto: MedicamentoDetailDto = this.detailDto,
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

        // El aviso es una sola fila que lleva hasta el chip; el nombre de la alergia solo va en él.
        composeRule.onNodeWithText("Desrecomendado para ti").assertHasClickAction()
        composeRule.onNodeWithText("Por una alergia marcada abajo").assertExists()
        composeRule.onNodeWithText("Paracetamol").assertDoesNotExist()
        composeRule.onNodeWithText("Principio activo").assertDoesNotExist()
        composeRule.onNodeWithText("Añadir al historial").assertIsNotEnabled()
        // El motivo ya está en la etiqueta: no se repite bajo el botón.
        composeRule.onNodeWithText("No puedes añadirlo", substring = true).assertDoesNotExist()
    }

    @Test
    fun addToHistoryIsDisabledWhenAFamilyAllergyMatches() {
        setContent(
            listOf(Allergy(id = "1", tipo = AllergyType.FAMILIA, atcCodigo = "N02B", atcNombre = "Analgésicos")),
        )

        composeRule.onNodeWithText("Por una alergia marcada abajo").assertExists()
        composeRule.onNodeWithText("Familia · N02B").assertDoesNotExist()
        composeRule.onNodeWithText("Añadir al historial").assertIsNotEnabled()
        composeRule.onNodeWithText("Crear pauta").assertIsNotEnabled()
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
        composeRule.onNodeWithText("Motivos: Fiebre").assertExists()
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

    @Test
    fun tappingThePhotoOpensItFullScreenAndTheCloseButtonDismissesIt() {
        val thumbnail = "https://cima.aemps.es/cima/fotos/thumbnails/materialas/77758/77758_materialas.jpg"
        setContent(
            allergies = emptyList(),
            detailDto = detailDto.copy(fotos = listOf(FotoDto(tipo = "materialas", url = thumbnail))),
        )

        composeRule.onNodeWithTag("medicationPhotoViewer").assertDoesNotExist()
        composeRule.onNode(enlargePhoto)
            .performClick()
        composeRule.onNodeWithTag("medicationPhotoViewer").assertExists()

        composeRule.onNodeWithContentDescription("Cerrar foto").performClick()
        composeRule.onNodeWithTag("medicationPhotoViewer").assertDoesNotExist()
    }

    @Test
    fun withoutAPhotoTheThumbnailIsNotClickable() {
        setContent(emptyList())

        composeRule.onNode(enlargePhoto)
            .assertDoesNotExist()
    }
}
