package com.medicapp.data.repository

import com.medicapp.data.remote.CimaApiService
import com.medicapp.data.remote.HEADER_SERVED_FROM_CACHE
import com.medicapp.model.AtcCode
import com.medicapp.model.MedicationDetail
import com.medicapp.model.PrincipioActivo
import retrofit2.HttpException

private const val MAESTRA_PRINCIPIOS_ACTIVOS = 1
private const val MAESTRA_ATC = 7

class MedicationCatalogRepository(
    private val apiService: CimaApiService,
) {
    suspend fun getMedicationDetail(nregistro: String): MedicationDetail {
        val dto = apiService.obtenerMedicamento(nregistro)
        return MedicationDetail(
            nregistro = dto.nregistro,
            nombre = dto.nombre,
            labtitular = dto.labtitular,
            dosis = dto.dosis,
            comercializado = dto.comerc,
            requiereReceta = dto.receta,
            fotoUrl = dto.fotos
                ?.firstOrNull { it.tipo == "materialas" }
                ?.url
                ?: dto.fotos?.firstOrNull()?.url,
            principiosActivos = dto.principiosActivos.orEmpty().map {
                PrincipioActivo(id = it.id, nombre = it.nombre, cantidad = it.cantidad, unidad = it.unidad)
            },
            atcs = dto.atcs.orEmpty().map {
                AtcCode(codigo = it.codigo, nombre = it.nombre, nivel = it.nivel ?: 0)
            },
        )
    }

    suspend fun searchPrincipiosActivos(query: String): CatalogResult<PrincipioActivo> {
        val response = apiService.obtenerMaestra(MAESTRA_PRINCIPIOS_ACTIVOS, query.ifBlank { null })
        val body = response.body()
        if (!response.isSuccessful || body == null) throw HttpException(response)
        return CatalogResult(
            items = body.resultados
                .filter { it.id != null }
                .map { PrincipioActivo(id = it.id!!, nombre = it.nombre) },
            isFromCache = response.headers()[HEADER_SERVED_FROM_CACHE] != null,
        )
    }

    suspend fun searchAtcCodes(query: String): CatalogResult<AtcCode> {
        val response = apiService.obtenerMaestra(MAESTRA_ATC, query.ifBlank { null })
        val body = response.body()
        if (!response.isSuccessful || body == null) throw HttpException(response)
        return CatalogResult(
            items = body.resultados
                .filter { it.codigo != null }
                .map { AtcCode(codigo = it.codigo!!, nombre = it.nombre, nivel = it.nivel ?: 0) },
            isFromCache = response.headers()[HEADER_SERVED_FROM_CACHE] != null,
        )
    }
}

data class CatalogResult<T>(
    val items: List<T>,
    /** Los resultados salieron de la caché: pueden estar desfasados. */
    val isFromCache: Boolean = false,
)
