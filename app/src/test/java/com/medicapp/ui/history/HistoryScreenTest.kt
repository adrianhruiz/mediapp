package com.medicapp.ui.history

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
        onOpenMedication: (TakenEntry) -> Unit,
    ) {
        val viewModel = HistoryViewModel(
            FakeMedicationHistoryRepository(entries),
            FakeReasonRepository(),
        )
        composeRule.setContent {
            HistoryScreen(
                contentPadding = PaddingValues(),
                onEditEntry = {},
                onOpenMedication = onOpenMedication,
                viewModel = viewModel,
            )
        }
    }

    @Test
    fun `clicking a history entry opens the medication it refers to`() {
        var opened: TakenEntry? = null
        setContent(listOf(entry("1", "12345", "Ibuprofeno"))) { opened = it }

        composeRule.onNodeWithContentDescription("Ver ficha de Ibuprofeno").performClick()

        assertEquals("12345", opened?.nregistro)
    }

    @Test
    fun `an entry without a medication reference is not clickable`() {
        var opened: TakenEntry? = null
        setContent(listOf(entry("1", "", "Ibuprofeno"))) { opened = it }

        composeRule.onNodeWithContentDescription("Ver ficha de Ibuprofeno").performClick()

        assertNull(opened)
    }
}
