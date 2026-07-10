package com.medicapp.data.repository

import com.medicapp.data.remote.CimaApiService
import com.medicapp.model.AtcCode
import com.medicapp.model.MedicationDetail
import com.medicapp.model.PrincipioActivo

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

    suspend fun searchPrincipiosActivos(query: String): List<PrincipioActivo> =
        apiService.obtenerMaestra(maestra = MAESTRA_PRINCIPIOS_ACTIVOS, nombre = query.ifBlank { null })
            .resultados
            .filter { it.id != null }
            .map { PrincipioActivo(id = it.id!!, nombre = it.nombre) }

    suspend fun searchAtcCodes(query: String): List<AtcCode> =
        apiService.obtenerMaestra(maestra = MAESTRA_ATC, nombre = query.ifBlank { null })
            .resultados
            .filter { it.codigo != null }
            .map { AtcCode(codigo = it.codigo!!, nombre = it.nombre, nivel = it.nivel ?: 0) }
}
