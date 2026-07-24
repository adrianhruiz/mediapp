package com.medicapp.data.remote

import com.medicapp.data.remote.dto.MaestrasResponseDto
import com.medicapp.data.remote.dto.MedicamentoDetailDto
import com.medicapp.data.remote.dto.MedicamentosResponseDto
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Query

interface CimaApiService {

    /**
     * Devuelve la respuesta completa, no solo el cuerpo, porque el repositorio necesita mirar
     * la cabecera [HEADER_SERVED_FROM_CACHE] para saber si los resultados salieron de caché.
     */
    @GET("medicamentos")
    suspend fun buscarMedicamentos(
        @Query("nombre") nombre: String,
        @Query("pagina") pagina: Int = 1,
    ): Response<MedicamentosResponseDto>

    @GET("medicamento")
    suspend fun obtenerMedicamento(
        @Query("nregistro") nregistro: String,
    ): MedicamentoDetailDto

    /** Devuelve la respuesta completa por lo mismo que [buscarMedicamentos]. */
    @GET("maestras")
    suspend fun obtenerMaestra(
        @Query("maestra") maestra: Int,
        @Query("nombre") nombre: String? = null,
    ): Response<MaestrasResponseDto>
}
