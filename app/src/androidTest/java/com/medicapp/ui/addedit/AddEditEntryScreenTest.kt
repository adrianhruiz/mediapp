package com.medicapp.ui.addedit

import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.medicapp.data.repository.FakeMedicationHistoryRepository
import com.medicapp.data.repository.FakeReasonRepository
import com.medicapp.model.TakenEntry
import com.medicapp.ui.theme.MedicAppTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AddEditEntryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val existingEntry = TakenEntry(
        id = "entry-1",
        nregistro = "77758",
        nombreMedicamento = "Ibuprofeno",
        reasonId = "r1",
        reasonNombre = "Dolor de cabeza",
        timestampMillis = 0L,
        dosisSnapshot = null,
        fotoUrl = null,
    )

    private fun setContent(history: FakeMedicationHistoryRepository) {
        val viewModel = AddEditEntryViewModel(
            history,
            FakeReasonRepository(),
            SavedStateHandle(mapOf("nregistro" to "77758", "nombre" to "Ibuprofeno")),
        )
        composeRule.setContent {
            MedicAppTheme {
                AddEditEntryScreen(onDone = {}, onCancel = {}, viewModel = viewModel)
            }
        }
    }

    @Test
    fun savingIsBlockedWhenTheMedicationAlreadyHasThatReason() {
        setContent(FakeMedicationHistoryRepository(listOf(existingEntry)))

        composeRule.onNodeWithText("Motivo (ej. dolor de cabeza)").performTextInput("Dolor de cabeza")

        composeRule.onNodeWithText("Ya tienes este medicamento en el historial con ese motivo.").assertExists()
        composeRule.onNodeWithText("Guardar").assertIsNotEnabled()
    }

    @Test
    fun savingIsAllowedWithADifferentReason() {
        setContent(FakeMedicationHistoryRepository(listOf(existingEntry)))

        composeRule.onNodeWithText("Motivo (ej. dolor de cabeza)").performTextInput("Fiebre")

        composeRule.onNodeWithText("Guardar").assertIsEnabled()
    }

    @Test
    fun addingAnEntryAndReenteringTheSameReasonBlocksSavingAgain() {
        val history = FakeMedicationHistoryRepository()
        setContent(history)

        composeRule.onNodeWithText("Motivo (ej. dolor de cabeza)").performTextInput("Fiebre")
        composeRule.onNodeWithText("Guardar").performClick()
        composeRule.waitForIdle()

        assertEquals(1, history.entries.size)
        composeRule.onNodeWithText("Guardar").assertIsNotEnabled()
    }
}
