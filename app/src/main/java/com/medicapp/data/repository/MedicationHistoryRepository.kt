package com.medicapp.data.repository

import com.medicapp.model.TakenEntry
import kotlinx.coroutines.flow.Flow

interface MedicationHistoryRepository {

    fun getPage(limit: Long): Flow<PagedResult<TakenEntry>>

    /** Entradas del historial de un medicamento concreto, para detectar duplicados. */
    fun getByMedication(nregistro: String): Flow<List<TakenEntry>>

    /** Números de registro que ya están en el historial, para marcarlos como añadidos. */
    fun getMedicationNregistros(): Flow<Set<String>>

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
