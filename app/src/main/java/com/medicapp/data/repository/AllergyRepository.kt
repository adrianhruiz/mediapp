package com.medicapp.data.repository

import com.medicapp.model.Allergy
import com.medicapp.model.AtcCode
import com.medicapp.model.PrincipioActivo
import kotlinx.coroutines.flow.Flow

interface AllergyRepository {

    fun getAll(): Flow<List<Allergy>>

    suspend fun addPrincipioActivo(id: Int, nombre: String)

    suspend fun addFamilia(atcCodigo: String, atcNombre: String)

    /**
     * Alta en bloque, para "Añadir todas": una sola ida y vuelta en lugar de una por resultado.
     * Repetir una alergia que ya está no la duplica ni hace falta comprobarlo antes.
     */
    suspend fun addPrincipiosActivos(principiosActivos: List<PrincipioActivo>)

    suspend fun addFamilias(familias: List<AtcCode>)

    suspend fun delete(allergyId: String)
}
