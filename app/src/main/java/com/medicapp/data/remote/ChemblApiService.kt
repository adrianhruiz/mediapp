package com.medicapp.data.remote

import com.medicapp.data.remote.dto.ChemblAtcClassDto
import com.medicapp.data.remote.dto.ChemblAtcListDto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.QueryMap

/** API de ChEMBL (EBI): da la clasificación ATC con los nombres oficiales de la OMS en inglés. */
interface ChemblApiService {

    /** Solo admite códigos de nivel 5, que son la clave de cada fila. */
    @GET("atc_class/{code}.json")
    suspend fun atcClass(@Path("code") code: String): ChemblAtcClassDto

    /** Filas que comparten un código de nivel 1 a 4, por ejemplo `level3=M01A`. */
    @GET("atc_class.json")
    suspend fun atcClasses(@QueryMap filter: Map<String, String>): ChemblAtcListDto
}
