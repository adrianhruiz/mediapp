package com.medicapp.data.repository

/**
 * Resumen de qué medicamentos hay en el historial, en un único documento.
 *
 * La búsqueda marca con un distintivo los medicamentos que ya están en el historial, y para
 * saberlo se leía la colección entera. Con doscientas entradas eso son doscientas lecturas cada
 * vez que se abre la búsqueda, para acabar usando solo una lista de números de registro.
 *
 * Aquí se guarda esa lista ya hecha: un documento con un contador por medicamento. Leerlo cuesta
 * una lectura, dé igual lo grande que sea el historial.
 *
 * Es un contador y no una lista de sí/no porque el mismo medicamento puede estar varias veces
 * con motivos distintos: borrar una de esas entradas no debe quitar el distintivo mientras
 * queden otras.
 */
object HistorySummary {
    const val COLLECTION = "summaries"
    const val DOCUMENT = "historyMedications"
    const val NREGISTROS_FIELD = "nregistros"

    /**
     * Los medicamentos que siguen teniendo alguna entrada.
     *
     * Al borrar se resta uno y se deja el contador a cero en vez de quitar la clave: quitarla
     * exigiría leer el documento antes de escribir, y restar no exige nada. Los ceros se filtran
     * aquí, que es donde importan.
     */
    fun nregistrosWithEntries(counts: Map<String, Any?>?): Set<String> = counts.orEmpty()
        .filterValues { ((it as? Number)?.toLong() ?: 0L) > 0L }
        .keys

    /** Cuenta las entradas de cada medicamento, para rehacer el resumen desde el historial. */
    fun countByNregistro(nregistros: List<String>): Map<String, Long> = nregistros
        .groupingBy { it }
        .eachCount()
        .mapValues { (_, count) -> count.toLong() }
}
