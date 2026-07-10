package com.medicapp.data.repository

import com.medicapp.data.remote.CimaApiService
import com.medicapp.data.remote.dto.MedicamentoDto
import com.medicapp.model.Medication

class MedicationSearchRepository(
    private val apiService: CimaApiService,
) {
    suspend fun search(nombre: String): SearchResult {
        val response = apiService.buscarMedicamentos(nombre = nombre)
        return SearchResult(
            medications = response.resultados.map { it.toDomain() },
            totalFilas = response.totalFilas,
        )
    }

    private fun MedicamentoDto.toDomain() = Medication(
        nregistro = nregistro,
        nombre = nombre,
        labtitular = labtitular,
        dosis = dosis,
        comercializado = comerc,
        requiereReceta = receta,
        fotoUrl = fotos
            ?.firstOrNull { it.tipo == "materialas" }
            ?.url
            ?: fotos?.firstOrNull()?.url,
    )
}

data class SearchResult(
    val medications: List<Medication>,
    val totalFilas: Int,
)
