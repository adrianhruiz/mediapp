package com.medicapp.data.repository

/** Nombre oficial en inglés de un código ATC, o null si no hay uno que corresponda exactamente. */
fun interface AtcEnglishNameSource {
    suspend fun englishName(atcCode: String): String?
}
