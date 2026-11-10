package com.medicapp.data.remote

import com.medicapp.data.remote.dto.MaestrasResponseDto
import com.medicapp.data.remote.dto.MedicamentoDetailDto
import com.medicapp.data.remote.dto.MedicamentosResponseDto
import kotlinx.coroutines.delay
import okhttp3.Headers
import retrofit2.Response

class FakeCimaApiService(
    private val medicamentosResponse: MedicamentosResponseDto? = null,
    private val medicamentoDetailResponse: MedicamentoDetailDto? = null,
    private val maestrasResponse: MaestrasResponseDto? = null,
    /** Simula que la búsqueda se ha servido desde la caché de disco. */
    private val medicamentosFromCache: Boolean = false,
    /** Lo mismo para las consultas al catálogo (principios activos y familias). */
    private val maestrasFromCache: Boolean = false,
    /** Si no es nulo, la búsqueda lanza este error en vez de responder. */
    private val medicamentosError: Exception? = null,
    /** Simula un CIMA lento: la búsqueda tarda esto antes de responder. */
    private val medicamentosDelayMillis: Long = 0L,
) : CimaApiService {

    var lastBuscarMedicamentosNombre: String? = null
        private set
    var lastMaestraId: Int? = null
        private set
    var lastMaestraNombre: String? = null
        private set

    override suspend fun buscarMedicamentos(nombre: String, pagina: Int): Response<MedicamentosResponseDto> {
        lastBuscarMedicamentosNombre = nombre
        if (medicamentosDelayMillis > 0) delay(medicamentosDelayMillis)
        medicamentosError?.let { throw it }
        val body = medicamentosResponse ?: error("No medicamentosResponse configured on FakeCimaApiService")
        val headers = if (medicamentosFromCache) {
            Headers.headersOf(HEADER_SERVED_FROM_CACHE, "true")
        } else {
            Headers.headersOf()
        }
        return Response.success(body, headers)
    }

    override suspend fun obtenerMedicamento(nregistro: String): MedicamentoDetailDto {
        return medicamentoDetailResponse ?: error("No medicamentoDetailResponse configured on FakeCimaApiService")
    }

    override suspend fun obtenerMaestra(maestra: Int, nombre: String?): Response<MaestrasResponseDto> {
        lastMaestraId = maestra
        lastMaestraNombre = nombre
        val body = maestrasResponse ?: error("No maestrasResponse configured on FakeCimaApiService")
        val headers = if (maestrasFromCache) {
            Headers.headersOf(HEADER_SERVED_FROM_CACHE, "true")
        } else {
            Headers.headersOf()
        }
        return Response.success(body, headers)
    }
}
