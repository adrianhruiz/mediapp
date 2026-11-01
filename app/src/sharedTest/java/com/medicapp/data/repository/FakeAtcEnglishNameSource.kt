package com.medicapp.data.repository

class FakeAtcEnglishNameSource(
    private val names: Map<String, String>,
    /** Cuántas veces falla cada código antes de responder, como sin red. */
    failures: Map<String, Int> = emptyMap(),
    /** Se ejecuta en mitad de la consulta, para simular que el usuario escribe a la vez. */
    private val duringLookup: suspend (String) -> Unit = {},
) : AtcEnglishNameSource {

    private val pendingFailures = failures.toMutableMap()

    /** Códigos consultados, para comprobar que no se repiten consultas innecesarias. */
    val requested = mutableListOf<String>()

    override suspend fun englishName(atcCode: String): AtcLookup {
        requested += atcCode
        duringLookup(atcCode)
        val left = pendingFailures[atcCode] ?: 0
        if (left > 0) {
            pendingFailures[atcCode] = left - 1
            return AtcLookup.Failed
        }
        return names[atcCode]?.let { AtcLookup.Found(it) } ?: AtcLookup.NotFound
    }
}
