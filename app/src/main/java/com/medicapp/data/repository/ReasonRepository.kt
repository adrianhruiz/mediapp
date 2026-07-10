package com.medicapp.data.repository

import com.medicapp.model.Reason
import kotlinx.coroutines.flow.Flow

interface ReasonRepository {

    fun getAll(): Flow<List<Reason>>

    suspend fun findOrCreate(nombre: String): Reason
}
