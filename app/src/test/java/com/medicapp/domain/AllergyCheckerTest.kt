package com.medicapp.domain

import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import com.medicapp.model.AtcCode
import com.medicapp.model.MedicationDetail
import com.medicapp.model.PrincipioActivo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AllergyCheckerTest {

    private fun medication(
        principiosActivos: List<PrincipioActivo> = emptyList(),
        atcs: List<AtcCode> = emptyList(),
    ) = MedicationDetail(
        nregistro = "1",
        nombre = "Medicamento",
        labtitular = null,
        dosis = null,
        comercializado = true,
        requiereReceta = false,
        fotoUrl = null,
        principiosActivos = principiosActivos,
        atcs = atcs,
    )

    @Test
    fun `no allergies means no matches`() {
        val medication = medication(principiosActivos = listOf(PrincipioActivo(id = 1, nombre = "Ibuprofeno")))

        val matches = AllergyChecker.findMatches(medication, emptyList())

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `matches by active ingredient id`() {
        val medication = medication(principiosActivos = listOf(PrincipioActivo(id = 745, nombre = "Paracetamol")))
        val allergy = Allergy(
            id = "a1",
            tipo = AllergyType.PRINCIPIO_ACTIVO,
            principioActivoId = 745,
            principioActivoNombre = "Paracetamol",
        )

        val matches = AllergyChecker.findMatches(medication, listOf(allergy))

        assertEquals(1, matches.size)
        assertEquals("a1", matches.first().allergy.id)
    }

    @Test
    fun `does not match a different active ingredient id`() {
        val medication = medication(principiosActivos = listOf(PrincipioActivo(id = 1, nombre = "Ibuprofeno")))
        val allergy = Allergy(
            id = "a1",
            tipo = AllergyType.PRINCIPIO_ACTIVO,
            principioActivoId = 999,
            principioActivoNombre = "Otro",
        )

        val matches = AllergyChecker.findMatches(medication, listOf(allergy))

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `matches by family when the medication ATC code starts with the allergy prefix`() {
        val medication = medication(atcs = listOf(AtcCode(codigo = "J01CA04", nombre = "Amoxicilina", nivel = 5)))
        val allergy = Allergy(id = "a1", tipo = AllergyType.FAMILIA, atcCodigo = "J01C", atcNombre = "Penicilinas")

        val matches = AllergyChecker.findMatches(medication, listOf(allergy))

        assertEquals(1, matches.size)
    }

    @Test
    fun `does not match a sibling ATC branch, like cephalosporins vs penicillins`() {
        val medication = medication(atcs = listOf(AtcCode(codigo = "J01DB01", nombre = "Cefalexina", nivel = 5)))
        val allergy = Allergy(id = "a1", tipo = AllergyType.FAMILIA, atcCodigo = "J01C", atcNombre = "Penicilinas")

        val matches = AllergyChecker.findMatches(medication, listOf(allergy))

        assertTrue(matches.isEmpty())
    }

    @Test
    fun `returns one match per allergy when both ingredient and family allergies apply`() {
        val medication = medication(
            principiosActivos = listOf(PrincipioActivo(id = 745, nombre = "Paracetamol")),
            atcs = listOf(AtcCode(codigo = "N02BE01", nombre = "Paracetamol", nivel = 5)),
        )
        val allergies = listOf(
            Allergy(
                id = "a1",
                tipo = AllergyType.PRINCIPIO_ACTIVO,
                principioActivoId = 745,
                principioActivoNombre = "Paracetamol",
            ),
            Allergy(id = "a2", tipo = AllergyType.FAMILIA, atcCodigo = "N02B", atcNombre = "Analgesicos"),
        )

        val matches = AllergyChecker.findMatches(medication, allergies)

        assertEquals(2, matches.size)
    }
}
