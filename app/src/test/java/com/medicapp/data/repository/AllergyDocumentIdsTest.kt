package com.medicapp.data.repository

import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AllergyDocumentIdsTest {

    private fun principioActivo(id: String, principioActivoId: Int?) = Allergy(
        id = id,
        tipo = AllergyType.PRINCIPIO_ACTIVO,
        principioActivoId = principioActivoId,
        principioActivoNombre = "IBUPROFENO",
    )

    private fun familia(id: String, atcCodigo: String?) = Allergy(
        id = id,
        tipo = AllergyType.FAMILIA,
        atcCodigo = atcCodigo,
        atcNombre = "Antiinflamatorios",
    )

    @Test
    fun `the same allergy always lands on the same document`() {
        assertEquals(principioActivoDocumentId(214), principioActivoDocumentId(214))
        assertEquals(familiaDocumentId("M01AE"), familiaDocumentId("m01ae"))
    }

    @Test
    fun `an ingredient and a family can never collide`() {
        assertTrue(principioActivoDocumentId(1) != familiaDocumentId("1"))
    }

    @Test
    fun `an allergy without its key data is left alone`() {
        assertNull(deterministicAllergyId(principioActivo(id = "vieja", principioActivoId = null)))
        assertNull(deterministicAllergyId(familia(id = "vieja", atcCodigo = "  ")))
    }

    @Test
    fun `only the allergies saved with a drawn id are rewritten`() {
        val yaMigrada = principioActivo(id = principioActivoDocumentId(214), principioActivoId = 214)
        val antigua = familia(id = "8sJk2Lqp", atcCodigo = "M01AE")

        val plan = planAllergyIdMigration(listOf(yaMigrada, antigua))

        assertEquals(mapOf("8sJk2Lqp" to familiaDocumentId("M01AE")), plan)
    }

    @Test
    fun `two old copies of one allergy end up merged into a single document`() {
        val plan = planAllergyIdMigration(
            listOf(
                principioActivo(id = "primera", principioActivoId = 214),
                principioActivo(id = "segunda", principioActivoId = 214),
            ),
        )

        assertEquals(setOf("primera", "segunda"), plan.keys)
        assertEquals(setOf(principioActivoDocumentId(214)), plan.values.toSet())
    }

    @Test
    fun `nothing to rewrite once everything is migrated`() {
        val migradas = listOf(
            principioActivo(id = principioActivoDocumentId(214), principioActivoId = 214),
            familia(id = familiaDocumentId("M01AE"), atcCodigo = "M01AE"),
        )

        assertTrue(planAllergyIdMigration(migradas).isEmpty())
    }
}
