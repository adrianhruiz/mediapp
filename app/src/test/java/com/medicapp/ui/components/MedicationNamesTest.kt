package com.medicapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationNamesTest {

    @Test
    fun `uppercase CIMA names get an initial capital per word`() {
        assertEquals(
            "Ibuprofeno (Arginina) Cinfa 600 mg Granulado para Solucion Oral EFG",
            MedicationNames.pretty("IBUPROFENO (ARGININA) CINFA 600 mg GRANULADO PARA SOLUCION ORAL EFG"),
        )
    }

    @Test
    fun `names already written in mixed case are left alone`() {
        assertEquals("Augmentine 875/125 mg", MedicationNames.pretty("Augmentine 875/125 mg"))
    }

    @Test
    fun `units and dose numbers stay lowercase`() {
        assertEquals("Paracetamol Kern 1 g Comprimidos EFG", MedicationNames.pretty("PARACETAMOL KERN 1 G COMPRIMIDOS EFG"))
        assertEquals("Grippostad 200mg/5mg", MedicationNames.pretty("GRIPPOSTAD 200MG/5MG"))
    }

    @Test
    fun `split separates the brand from dose and form`() {
        assertEquals(
            "Omeprazol Normon" to "20 mg capsulas duras EFG",
            MedicationNames.split("OMEPRAZOL NORMON 20 mg CAPSULAS DURAS EFG"),
        )
    }

    @Test
    fun `dose and form read in lowercase except acronyms`() {
        assertEquals("1.000.000 U.I. polvo y disolvente", MedicationNames.detail("1.000.000 U.i. Polvo Y Disolvente"))
        assertEquals("1 millones UI", MedicationNames.detail("1 MILLONES UI"))
    }

    @Test
    fun `split keeps everything as title when there is no number`() {
        assertEquals("Dalsy Naranja" to null, MedicationNames.split("DALSY NARANJA"))
    }
}
