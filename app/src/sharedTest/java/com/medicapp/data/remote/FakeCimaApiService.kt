package com.medicapp.data.remote

import com.medicapp.data.remote.dto.MaestrasResponseDto
import com.medicapp.data.remote.dto.MedicamentoDetailDto
import com.medicapp.data.remote.dto.MedicamentosResponseDto

class FakeCimaApiService(
    private val medicamentosResponse: MedicamentosResponseDto? = null,
    private val medicamentoDetailResponse: MedicamentoDetailDto? = null,
    private val maestrasResponse: MaestrasResponseDto? = null,
) : CimaApiService {

    var lastBuscarMedicamentosNombre: String? = null
        private set
    var lastMaestraId: Int? = null
        private set
    var lastMaestraNombre: String? = null
        private set

    override suspend fun buscarMedicamentos(nombre: String, pagina: Int): MedicamentosResponseDto {
        lastBuscarMedicamentosNombre = nombre
        return medicamentosResponse ?: error("No medicamentosResponse configured on FakeCimaApiService")
    }

    override suspend fun obtenerMedicamento(nregistro: String): MedicamentoDetailDto {
        return medicamentoDetailResponse ?: error("No medicamentoDetailResponse configured on FakeCimaApiService")
    }

    override suspend fun obtenerMaestra(maestra: Int, nombre: String?): MaestrasResponseDto {
        lastMaestraId = maestra
        lastMaestraNombre = nombre
        return maestrasResponse ?: error("No maestrasResponse configured on FakeCimaApiService")
    }
}
