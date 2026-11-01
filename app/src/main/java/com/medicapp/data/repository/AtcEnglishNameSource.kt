package com.medicapp.data.repository

/** Resultado de buscar el nombre oficial en inglés de un código ATC. */
sealed interface AtcLookup {
    data class Found(val name: String) : AtcLookup

    /** No hay un nombre que corresponda exactamente a ese código: no se vuelve a preguntar. */
    data object NotFound : AtcLookup

    /** No se pudo preguntar (sin red, servidor caído): vale la pena reintentar. */
    data object Failed : AtcLookup
}

fun interface AtcEnglishNameSource {
    suspend fun englishName(atcCode: String): AtcLookup
}
