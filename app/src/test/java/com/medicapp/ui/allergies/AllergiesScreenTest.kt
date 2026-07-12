package com.medicapp.ui.allergies

import android.app.Application
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import com.medicapp.data.remote.FakeCimaApiService
import com.medicapp.data.repository.FakeAllergyRepository
import com.medicapp.data.repository.MedicationCatalogRepository
import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// La Application real inicializa Firebase, que no arranca fuera del dispositivo.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class AllergiesScreenTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun setContent(allergies: List<Allergy>) {
        val viewModel = AllergiesViewModel(
            FakeAllergyRepository(allergies),
            MedicationCatalogRepository(FakeCimaApiService()),
        )
        composeRule.setContent {
            AllergiesScreen(contentPadding = PaddingValues(), viewModel = viewModel)
        }
    }

    @Test
    fun `a family allergy shows its ATC code so look-alike names can be told apart`() {
        setContent(
            listOf(
                Allergy(
                    id = "1",
                    tipo = AllergyType.FAMILIA,
                    atcCodigo = "J01C",
                    atcNombre = "ANTIBACTERIANOS BETALACTAMICOS, PENICILINAS",
                ),
                Allergy(
                    id = "2",
                    tipo = AllergyType.FAMILIA,
                    atcCodigo = "J01CA",
                    atcNombre = "ANTIBACTERIANOS BETALACTAMICOS, PENICILINAS",
                ),
            ),
        )

        composeRule.onNodeWithText("J01C").assertIsDisplayed()
        composeRule.onNodeWithText("J01CA").assertIsDisplayed()
    }
}
