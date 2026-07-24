package com.medicapp.data.repository

import com.medicapp.data.remote.CimaApiService
import com.medicapp.data.remote.HEADER_SERVED_FROM_CACHE
import com.medicapp.data.remote.dto.MedicamentoDto
import com.medicapp.model.Medication
import retrofit2.HttpException

class MedicationSearchRepository(
    private val apiService: CimaApiService,
) {
    suspend fun search(nombre: String): SearchResult {
        val response = apiService.buscarMedicamentos(nombre = nombre)
        val body = response.body()
        if (!response.isSuccessful || body == null) throw HttpException(response)
        return SearchResult(
            medications = body.resultados.map { it.toDomain() },
            totalFilas = body.totalFilas,
            isFromCache = response.headers()[HEADER_SERVED_FROM_CACHE] != null,
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
    /** Los resultados salieron de la caché: pueden estar desfasados. */
    val isFromCache: Boolean = false,
)
