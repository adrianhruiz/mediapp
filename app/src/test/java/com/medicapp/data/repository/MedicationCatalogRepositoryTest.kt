package com.medicapp.data.repository

import com.medicapp.data.remote.FakeCimaApiService
import com.medicapp.data.remote.dto.AtcDto
import com.medicapp.data.remote.dto.FotoDto
import com.medicapp.data.remote.dto.MaestraItemDto
import com.medicapp.data.remote.dto.MaestrasResponseDto
import com.medicapp.data.remote.dto.MedicamentoDetailDto
import com.medicapp.data.remote.dto.PrincipioActivoDto
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedicationCatalogRepositoryTest {

    @Test
    fun `getMedicationDetail maps principiosActivos, atcs and fotoUrl`() = runTest {
        val detail = MedicamentoDetailDto(
            nregistro = "77758",
            nombre = "PARACETAMOL ABAMED 1G",
            principiosActivos = listOf(PrincipioActivoDto(id = 745, nombre = "PARACETAMOL")),
            atcs = listOf(AtcDto(codigo = "N02BE01", nombre = "Paracetamol")),
            fotos = listOf(FotoDto(tipo = "materialas", url = "foto.jpg")),
        )
        val repository = MedicationCatalogRepository(FakeCimaApiService(medicamentoDetailResponse = detail))

        val result = repository.getMedicationDetail("77758")

        assertEquals(1, result.principiosActivos.size)
        assertEquals(745, result.principiosActivos.first().id)
        assertEquals(1, result.atcs.size)
        assertEquals("N02BE01", result.atcs.first().codigo)
        assertEquals("foto.jpg", result.fotoUrl)
    }

    @Test
    fun `getMedicationDetail leaves fotoUrl null when CIMA reports no photos`() = runTest {
        val detail = MedicamentoDetailDto(nregistro = "1", nombre = "XAZAL", fotos = null)
        val repository = MedicationCatalogRepository(FakeCimaApiService(medicamentoDetailResponse = detail))

        val result = repository.getMedicationDetail("1")

        assertNull(result.fotoUrl)
    }

    @Test
    fun `searchPrincipiosActivos discards entries without an id and queries maestra 1`() = runTest {
        val response = MaestrasResponseDto(
            totalFilas = 2,
            pagina = 1,
            tamanioPagina = 200,
            resultados = listOf(
                MaestraItemDto(id = 1, nombre = "Ibuprofeno"),
                MaestraItemDto(id = null, nombre = "Sin id"),
            ),
        )
        val fake = FakeCimaApiService(maestrasResponse = response)
        val repository = MedicationCatalogRepository(fake)

        val result = repository.searchPrincipiosActivos("ibuprofeno")

        assertEquals(1, result.items.size)
        assertEquals("Ibuprofeno", result.items.first().nombre)
        assertEquals(1, fake.lastMaestraId)
    }

    @Test
    fun `searchAtcCodes discards entries without a codigo and queries maestra 7`() = runTest {
        val response = MaestrasResponseDto(
            totalFilas = 2,
            pagina = 1,
            tamanioPagina = 200,
            resultados = listOf(
                MaestraItemDto(codigo = "J01C", nombre = "Penicilinas"),
                MaestraItemDto(codigo = null, nombre = "Sin codigo"),
            ),
        )
        val fake = FakeCimaApiService(maestrasResponse = response)
        val repository = MedicationCatalogRepository(fake)

        val result = repository.searchAtcCodes("penicilina")

        assertEquals(1, result.items.size)
        assertEquals("J01C", result.items.first().codigo)
        assertEquals(7, fake.lastMaestraId)
    }

    @Test
    fun `flags catalog results that came from the disk cache`() = runTest {
        val response = MaestrasResponseDto(
            totalFilas = 1,
            pagina = 1,
            tamanioPagina = 200,
            resultados = listOf(MaestraItemDto(id = 1, nombre = "Ibuprofeno")),
        )
        val repository = MedicationCatalogRepository(
            FakeCimaApiService(maestrasResponse = response, maestrasFromCache = true),
        )

        assertTrue(repository.searchPrincipiosActivos("ibuprofeno").isFromCache)
    }

    @Test
    fun `does not flag catalog results that came from the network`() = runTest {
        val response = MaestrasResponseDto(
            totalFilas = 1,
            pagina = 1,
            tamanioPagina = 200,
            resultados = listOf(MaestraItemDto(id = 1, nombre = "Ibuprofeno")),
        )
        val repository = MedicationCatalogRepository(FakeCimaApiService(maestrasResponse = response))

        assertFalse(repository.searchPrincipiosActivos("ibuprofeno").isFromCache)
    }

    @Test
    fun `a blank query is sent as null so CIMA returns the full catalog`() = runTest {
        val fake = FakeCimaApiService(maestrasResponse = MaestrasResponseDto(0, 1, 200, emptyList()))
        val repository = MedicationCatalogRepository(fake)

        repository.searchAtcCodes("")

        assertNull(fake.lastMaestraNombre)
    }
}
