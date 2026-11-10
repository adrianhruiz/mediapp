package com.medicapp.data.repository

import com.medicapp.model.SavedMedication
import com.medicapp.model.TakenEntry
import kotlinx.coroutines.flow.Flow

interface MedicationHistoryRepository {

    fun getPage(limit: Long): Flow<PagedResult<TakenEntry>>

    /** Entradas del historial de un medicamento concreto, para detectar duplicados. */
    fun getByMedication(nregistro: String): Flow<List<TakenEntry>>

    /**
     * Medicamentos que ya están en el historial, uno por número de registro, con su nombre y su
     * foto: para marcarlos como añadidos y para encontrarlos en la búsqueda sin pasar por CIMA.
     */
    fun getMedicationNregistros(): Flow<List<SavedMedication>>

    suspend fun getById(id: String): TakenEntry?

    /** Añade la entrada, salvo que ya exista una del mismo medicamento con el mismo motivo. */
    suspend fun add(
        nregistro: String,
        nombreMedicamento: String,
        reasonId: String,
        reasonNombre: String,
        dosisSnapshot: String?,
        fotoUrl: String?,
    ): String

    suspend fun updateReason(entry: TakenEntry, reasonId: String, reasonNombre: String)

    suspend fun delete(entry: TakenEntry)
}
