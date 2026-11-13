package com.medicapp.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationLabelTest {

    @Test
    fun `the presentation follows the commercial name`() {
        assertEquals(
            "Ibuprofeno Cinfa" to "600 mg comprimidos recubiertos EFG",
            nameAndPresentation("IBUPROFENO CINFA 600 mg COMPRIMIDOS RECUBIERTOS EFG", "600 mg"),
        )
    }

    @Test
    fun `a dose missing from the name goes first, separated by a dot`() {
        assertEquals(
            "Paracetamol Abamed" to "500 mg · 1 g comprimidos",
            nameAndPresentation("PARACETAMOL ABAMED 1 g COMPRIMIDOS", "500 mg"),
        )
        assertEquals("Xazal" to "5 mg", nameAndPresentation("XAZAL", "5 MG"))
    }

    @Test
    fun `a dose naming the active ingredient is not repeated when the name has the same amount`() {
        assertEquals(
            "Ebastina Cinfa" to "10 mg comprimidos recubiertos con pelicula EFG",
            nameAndPresentation("EBASTINA CINFA 10 mg COMPRIMIDOS RECUBIERTOS CON PELICULA EFG", "10 mg ebastina"),
        )
        assertEquals(
            "Dalsy" to "20 mg/ml suspensión oral",
            nameAndPresentation("DALSY 20 mg/ml SUSPENSIÓN ORAL", "20 MG/ML ibuprofeno"),
        )
    }

    @Test
    fun `a dose with a different amount is still added`() {
        assertEquals(
            "Ebastina Cinfa" to "20 mg ebastina · 10 mg comprimidos",
            nameAndPresentation("EBASTINA CINFA 10 mg COMPRIMIDOS", "20 mg ebastina"),
        )
    }

    @Test
    fun `without dose nor presentation there is no second line`() {
        assertEquals("Pomada casera" to null, nameAndPresentation("Pomada casera", null))
    }
}
