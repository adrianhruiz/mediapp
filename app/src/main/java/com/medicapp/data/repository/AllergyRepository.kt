package com.medicapp.data.repository

import com.medicapp.model.Allergy
import kotlinx.coroutines.flow.Flow

interface AllergyRepository {

    fun getAll(): Flow<List<Allergy>>

    suspend fun addPrincipioActivo(id: Int, nombre: String)

    suspend fun addFamilia(atcCodigo: String, atcNombre: String)

    suspend fun delete(allergyId: String)
}
