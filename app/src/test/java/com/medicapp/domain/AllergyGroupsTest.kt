package com.medicapp.domain

import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import org.junit.Assert.assertEquals
import org.junit.Test

class AllergyGroupsTest {

    private fun ingredient(id: String, nombre: String) =
        Allergy(id = id, tipo = AllergyType.PRINCIPIO_ACTIVO, principioActivoId = id.toInt(), principioActivoNombre = nombre)

    private fun family(id: String, codigo: String, nombre: String) =
        Allergy(id = id, tipo = AllergyType.FAMILIA, atcCodigo = codigo, atcNombre = nombre)

    @Test
    fun `families come apart from active ingredients and each group is sorted by name`() {
        val groups = AllergyGroups.of(
            listOf(
                ingredient("1", "METAMIZOL MAGNESICO"),
                family("2", "M01A", "PRODUCTOS ANTIINFLAMATORIOS"),
                ingredient("3", "Bencilpenicilina"),
                family("4", "J01C", "ANTIBACTERIANOS BETALACTAMICOS, PENICILINAS"),
            ),
        )

        assertEquals(listOf("4", "2"), groups.families.map { it.id })
        assertEquals(listOf("3", "1"), groups.ingredients.map { it.id })
    }
}
