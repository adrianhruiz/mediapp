package com.medicapp.data.repository

import com.medicapp.model.TakenEntry
import kotlinx.coroutines.flow.Flow

interface MedicationHistoryRepository {

    fun getAll(): Flow<List<TakenEntry>>

    suspend fun getById(id: String): TakenEntry?

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
