package com.medicapp.data.repository

class FakeAtcEnglishNameSource(private val names: Map<String, String>) : AtcEnglishNameSource {

    /** Códigos consultados, para comprobar que no se repiten consultas innecesarias. */
    val requested = mutableListOf<String>()

    override suspend fun englishName(atcCode: String): String? {
        requested += atcCode
        return names[atcCode]
    }
}
