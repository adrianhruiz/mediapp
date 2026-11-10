package com.medicapp.data.repository

import com.medicapp.model.SavedMedication
import java.util.concurrent.atomic.AtomicBoolean

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
 *
 * Además guarda nombre y foto de cada medicamento (campo `medicamentos`), para que la búsqueda
 * pueda enseñar lo que ya está en el historial sin depender de CIMA: los medicamentos propios no
 * están allí y antes no volvían a salir al buscarlos.
 */
object HistorySummary {
    const val COLLECTION = "summaries"
    const val DOCUMENT = "historyMedications"
    const val NREGISTROS_FIELD = "nregistros"
    const val MEDICAMENTOS_FIELD = "medicamentos"
    private const val NOMBRE_FIELD = "nombre"
    private const val FOTO_URL_FIELD = "fotoUrl"

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

    /** Lo que se guarda de un medicamento dentro de `medicamentos`. */
    fun medicationInfo(nombre: String, fotoUrl: String?): Map<String, Any?> =
        mapOf(NOMBRE_FIELD to nombre, FOTO_URL_FIELD to fotoUrl)

    /**
     * Los medicamentos que siguen teniendo alguna entrada, con su nombre y su foto.
     *
     * Salen de los contadores, como los distintivos de antes: un resumen antiguo sin nombres
     * sigue marcando todo lo que hay en el historial mientras [shouldRebuild] lo rehace. Al que le
     * falta el nombre se le deja vacío, y un nombre vacío no coincide con ninguna búsqueda.
     */
    fun savedMedications(counts: Map<String, Any?>?, medicamentos: Map<String, Any?>?): List<SavedMedication> =
        nregistrosWithEntries(counts).map { nregistro ->
            val info = medicamentos?.get(nregistro) as? Map<*, *>
            SavedMedication(nregistro, info?.get(NOMBRE_FIELD) as? String ?: "", info?.get(FOTO_URL_FIELD) as? String)
        }

    /**
     * Si hay que rehacer el resumen leyendo el historial entero.
     *
     * Se rehace cuando no existe y también cuando algún medicamento contado no tiene nombre: es
     * el caso de un resumen de antes de guardar nombres, aunque una entrada nueva ya le haya
     * añadido el suyo por merge. Tras rehacerlo todos lo tienen, así que solo pasa una vez.
     *
     * Sin respuesta del servidor ([fromServer] falso) no sabemos si falta o es que aún no ha
     * llegado; rehacerlo con esa duda escribiría un resumen a medias.
     */
    fun shouldRebuild(
        exists: Boolean,
        fromServer: Boolean,
        counts: Map<String, Any?>?,
        medicamentos: Map<String, Any?>?,
    ): Boolean {
        if (!fromServer) return false
        if (!exists || medicamentos == null) return true
        return nregistrosWithEntries(counts).any { nregistro ->
            (medicamentos[nregistro] as? Map<*, *>)?.get(NOMBRE_FIELD) !is String
        }
    }

    /**
     * El resumen completo a partir de las entradas del historial, una por entrada (repetidas
     * incluidas). Escribe siempre `medicamentos`, aunque vacío, para que [shouldRebuild] no vuelva
     * a pedirlo con un historial sin entradas.
     */
    fun rebuild(entries: List<SavedMedication>): Map<String, Any> = mapOf(
        NREGISTROS_FIELD to countByNregistro(entries.map { it.nregistro }),
        MEDICAMENTOS_FIELD to entries.groupBy { it.nregistro }.mapValues { (_, same) ->
            medicationInfo(same.first().nombre, same.firstNotNullOfOrNull { it.fotoUrl })
        },
    )

    /**
     * Decide, snapshot a snapshot de un mismo listener, cuándo lanzar la reconstrucción.
     *
     * El listener escucha también los cambios de metadatos, porque en un móvil que viene de la
     * versión anterior el resumen viejo llega primero de la caché y la confirmación del servidor
     * trae los mismos datos: sin metadatos esa confirmación no se recibe y no se rehace nunca.
     * A cambio llegan más avisos con el resumen viejo mientras se escribe el nuevo; esta puerta
     * deja pasar solo la primera petición hasta que la reconstrucción falle.
     */
    class RebuildGate {
        private val requested = AtomicBoolean(false)

        fun shouldRebuild(
            exists: Boolean,
            fromServer: Boolean,
            counts: Map<String, Any?>?,
            medicamentos: Map<String, Any?>?,
        ): Boolean = HistorySummary.shouldRebuild(exists, fromServer, counts, medicamentos) &&
            requested.compareAndSet(false, true)

        /** Si la reconstrucción falla se puede volver a pedir con el siguiente snapshot. */
        fun onRebuildFailed() {
            requested.set(false)
        }
    }
}
