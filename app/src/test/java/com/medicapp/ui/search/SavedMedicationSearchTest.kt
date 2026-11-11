package com.medicapp.ui.search

import com.medicapp.model.Medication
import com.medicapp.model.SavedMedication
import org.junit.Assert.assertEquals
import org.junit.Test

class SavedMedicationSearchTest {

    private val arnica = SavedMedication("propio-pomada-de-arnica", "Pomada de Árnica", null)
    private val xazal = SavedMedication("51234", "XAZAL 5 mg comprimidos", "https://foto")

    @Test
    fun `matches ignoring accents and case`() {
        assertEquals(listOf(arnica), SavedMedicationSearch.filter(listOf(arnica, xazal), "ARNICA"))
    }

    @Test
    fun `every word of the query must appear, in any order`() {
        assertEquals(listOf(arnica), SavedMedicationSearch.filter(listOf(arnica, xazal), "árnica pomada"))
        assertEquals(emptyList<SavedMedication>(), SavedMedicationSearch.filter(listOf(arnica, xazal), "arnica crema"))
    }

    @Test
    fun `a blank query matches nothing`() {
        assertEquals(emptyList<SavedMedication>(), SavedMedicationSearch.filter(listOf(arnica, xazal), "   "))
    }

    @Test
    fun `tapping a custom medication opens the custom dialog with its name`() {
        assertEquals(SavedSelection.Custom("Pomada de Árnica"), SavedMedicationSearch.selectionFor(arnica))
    }

    @Test
    fun `tapping a CIMA medication opens its sheet`() {
        assertEquals(
            SavedSelection.Detail(
                Medication(
                    nregistro = "51234",
                    nombre = "XAZAL 5 mg comprimidos",
                    labtitular = null,
                    dosis = null,
                    comercializado = null,
                    requiereReceta = null,
                    fotoUrl = "https://foto",
                ),
            ),
            SavedMedicationSearch.selectionFor(xazal),
        )
    }
}
