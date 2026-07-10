package com.medicapp.data.repository

import com.medicapp.data.remote.FakeCimaApiService
import com.medicapp.data.remote.dto.FotoDto
import com.medicapp.data.remote.dto.MedicamentoDto
import com.medicapp.data.remote.dto.MedicamentosResponseDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MedicationSearchRepositoryTest {

    @Test
    fun `search maps totalFilas and medications`() = runTest {
        val dto = MedicamentoDto(
            nregistro = "12345",
            nombre = "PARACETAMOL ABAMED 1G",
            labtitular = "Abamed",
            dosis = "1g",
            comerc = true,
            receta = false,
        )
        val response = MedicamentosResponseDto(totalFilas = 1, pagina = 1, tamanioPagina = 25, resultados = listOf(dto))
        val repository = MedicationSearchRepository(FakeCimaApiService(medicamentosResponse = response))

        val result = repository.search("paracetamol")

        assertEquals(1, result.totalFilas)
        val medication = result.medications.single()
        assertEquals("12345", medication.nregistro)
        assertEquals("PARACETAMOL ABAMED 1G", medication.nombre)
        assertNull(medication.fotoUrl)
    }

    @Test
    fun `prefers the materialas photo over other photo types`() = runTest {
        val dto = MedicamentoDto(
            nregistro = "1",
            nombre = "X",
            fotos = listOf(
                FotoDto(tipo = "formafarmac", url = "forma.jpg"),
                FotoDto(tipo = "materialas", url = "material.jpg"),
            ),
        )
        val repository = MedicationSearchRepository(
            FakeCimaApiService(medicamentosResponse = MedicamentosResponseDto(1, 1, 25, listOf(dto))),
        )

        val result = repository.search("x")

        assertEquals("material.jpg", result.medications.single().fotoUrl)
    }

    @Test
    fun `falls back to the first photo when no materialas photo exists`() = runTest {
        val dto = MedicamentoDto(
            nregistro = "1",
            nombre = "X",
            fotos = listOf(FotoDto(tipo = "formafarmac", url = "forma.jpg")),
        )
        val repository = MedicationSearchRepository(
            FakeCimaApiService(medicamentosResponse = MedicamentosResponseDto(1, 1, 25, listOf(dto))),
        )

        val result = repository.search("x")

        assertEquals("forma.jpg", result.medications.single().fotoUrl)
    }

    @Test
    fun `leaves fotoUrl null when CIMA has no photos for the medication, like Xazal`() = runTest {
        val dto = MedicamentoDto(nregistro = "1", nombre = "XAZAL", fotos = null)
        val repository = MedicationSearchRepository(
            FakeCimaApiService(medicamentosResponse = MedicamentosResponseDto(1, 1, 25, listOf(dto))),
        )

        val result = repository.search("xazal")

        assertNull(result.medications.single().fotoUrl)
    }
}
