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
    fun `without dose nor presentation there is no second line`() {
        assertEquals("Pomada casera" to null, nameAndPresentation("Pomada casera", null))
    }
}
