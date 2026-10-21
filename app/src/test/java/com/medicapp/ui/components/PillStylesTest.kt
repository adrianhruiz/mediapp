package com.medicapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class PillStylesTest {

    @Test
    fun `tablets are drawn as tablets regardless of case or accents`() {
        val style = PillStyles.forMedication("1", "Atorvastatina Cinfa 20 mg comprimidos recubiertos EFG")
        assertEquals(PillShape.TABLET, style.shape)
    }

    @Test
    fun `capsules are drawn as capsules`() {
        val style = PillStyles.forMedication("2", "OMEPRAZOL NORMON 20 mg CÁPSULAS DURAS GASTRORRESISTENTES EFG")
        assertEquals(PillShape.CAPSULE, style.shape)
    }

    @Test
    fun `a name without a dosage form falls back to a capsule`() {
        val style = PillStyles.forMedication("3", "IBUPROFENO (ARGININA) CINFA 600 mg GRANULADO PARA SOLUCION ORAL EFG")
        assertEquals(PillShape.CAPSULE, style.shape)
    }

    @Test
    fun `a medication always keeps the same colors`() {
        val first = PillStyles.forMedication("62411", "Omeprazol cápsulas")
        val again = PillStyles.forMedication("62411", "Omeprazol cápsulas")
        assertEquals(first, again)
        assertEquals(first.top, PillStyles.packColor("62411"))
    }
}
