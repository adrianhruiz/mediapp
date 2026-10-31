package com.medicapp.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CustomMedicationTest {

    @Test
    fun `the id comes from the name, so the same medication always gets the same id`() {
        assertEquals("propio-pomada-de-arnica", CustomMedication.idFor("Pomada de árnica"))
        assertEquals(CustomMedication.idFor("Pomada de árnica"), CustomMedication.idFor("  POMADA   de Arnica "))
    }

    @Test
    fun `symbols that Firestore treats specially never reach the id`() {
        assertEquals("propio-crema-0-1-v-2", CustomMedication.idFor("Crema 0.1% v/2"))
    }

    @Test
    fun `a name without letters or digits has no id`() {
        assertNull(CustomMedication.idFor("   "))
        assertNull(CustomMedication.idFor("%%%"))
    }

    @Test
    fun `custom ids are told apart from CIMA registration numbers`() {
        assertTrue(CustomMedication.isCustom("propio-pomada-de-arnica"))
        assertFalse(CustomMedication.isCustom("65466"))
        assertFalse(CustomMedication.isCustom(""))
    }
}
