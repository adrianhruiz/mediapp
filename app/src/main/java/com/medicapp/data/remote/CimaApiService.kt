package com.medicapp.data.remote

import com.medicapp.data.remote.dto.MaestrasResponseDto
import com.medicapp.data.remote.dto.MedicamentoDetailDto
import com.medicapp.data.remote.dto.MedicamentosResponseDto
import retrofit2.http.GET
import retrofit2.http.Query

interface CimaApiService {

    @GET("medicamentos")
    suspend fun buscarMedicamentos(
        @Query("nombre") nombre: String,
        @Query("pagina") pagina: Int = 1,
    ): MedicamentosResponseDto

    @GET("medicamento")
    suspend fun obtenerMedicamento(
        @Query("nregistro") nregistro: String,
    ): MedicamentoDetailDto

    @GET("maestras")
    suspend fun obtenerMaestra(
        @Query("maestra") maestra: Int,
        @Query("nombre") nombre: String? = null,
    ): MaestrasResponseDto
}
