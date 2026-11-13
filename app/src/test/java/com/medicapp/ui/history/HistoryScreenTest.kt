package com.medicapp.ui.history

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.medicapp.data.repository.FakeMedicationHistoryRepository
import com.medicapp.data.repository.FakeReasonRepository
import com.medicapp.model.TakenEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// La Application real inicializa Firebase, que no arranca fuera del dispositivo.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class HistoryScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun entry(id: String, nregistro: String, nombre: String) = TakenEntry(
        id = id,
        nregistro = nregistro,
        nombreMedicamento = nombre,
        reasonId = "r1",
        reasonNombre = "Dolor de cabeza",
        timestampMillis = 0L,
        dosisSnapshot = null,
        fotoUrl = null,
    )

    private fun setContent(
        entries: List<TakenEntry>,
        onEditEntry: (TakenEntry) -> Unit = {},
        onOpenMedication: (TakenEntry) -> Unit = {},
    ) {
        val viewModel = HistoryViewModel(
            FakeMedicationHistoryRepository(entries),
            FakeReasonRepository(),
        )
        composeRule.setContent {
            HistoryScreen(
                contentPadding = PaddingValues(),
                onEditEntry = onEditEntry,
                onOpenMedication = onOpenMedication,
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun `tapping an entry opens it for editing`() {
        var edited: TakenEntry? = null
        setContent(listOf(entry("1", "12345", "Ibuprofeno")), onEditEntry = { edited = it })

        composeRule.onNodeWithContentDescription("Abrir entrada de Ibuprofeno").performClick()

        assertEquals("1", edited?.id)
    }

    @Test
    fun `rows have no pencil or trash of their own`() {
        setContent(listOf(entry("1", "12345", "Ibuprofeno")))

        composeRule.onNodeWithContentDescription("Editar").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Eliminar").assertDoesNotExist()
    }

    @Test
    fun `entries are grouped under their month`() {
        setContent(listOf(entry("1", "12345", "Ibuprofeno")))

        // timestamp 0 es enero de 1970; el antetítulo se pinta en mayúsculas.
        composeRule.onNodeWithText("ENERO 1970", substring = true).assertExists()
    }

    @Test
    fun `tapping the thumbnail opens the medication it refers to`() {
        var opened: TakenEntry? = null
        setContent(listOf(entry("1", "12345", "Ibuprofeno")), onOpenMedication = { opened = it })

        composeRule.onNodeWithContentDescription("Ver ficha de Ibuprofeno").performClick()

        assertEquals("12345", opened?.nregistro)
    }

    @Test
    fun `an entry without a medication reference has no sheet to open`() {
        var opened: TakenEntry? = null
        setContent(listOf(entry("1", "", "Ibuprofeno")), onOpenMedication = { opened = it })

        composeRule.onNodeWithContentDescription("Ver ficha de Ibuprofeno").performClick()

        assertNull(opened)
    }
}
