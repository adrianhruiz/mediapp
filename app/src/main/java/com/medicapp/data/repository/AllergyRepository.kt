package com.medicapp.data.repository

import com.medicapp.model.Allergy
import com.medicapp.model.AtcCode
import com.medicapp.model.PrincipioActivo
import kotlinx.coroutines.flow.Flow

interface AllergyRepository {

    /** Si falla la lectura, emite una lista vacía: sirve donde una lista incompleta no engaña. */
    fun getAll(): Flow<List<Allergy>>

    /**
     * Como [getAll], pero un fallo de lectura llega como excepción. Es para el informe: ante un
     * error tiene que mostrar el error, nunca "sin alergias".
     */
    fun getAllOrThrow(): Flow<List<Allergy>>

    suspend fun addPrincipioActivo(id: Int, nombre: String)

    suspend fun addFamilia(atcCodigo: String, atcNombre: String)

    /**
     * Alta en bloque, para "Añadir todas": una sola ida y vuelta en lugar de una por resultado.
     * Repetir una alergia que ya está no la duplica ni hace falta comprobarlo antes.
     */
    suspend fun addPrincipiosActivos(principiosActivos: List<PrincipioActivo>)

    suspend fun addFamilias(familias: List<AtcCode>)

    suspend fun delete(allergyId: String)

    /**
     * Guarda la traducción oficial solo si la alergia sigue sin nombre en inglés: si el usuario
     * escribió uno mientras se consultaba, el suyo se queda.
     */
    suspend fun setOfficialEnglishNameIfMissing(allergyId: String, nombreIngles: String)

    /** Guarda el nombre en inglés de una alergia; null lo borra. */
    suspend fun setEnglishName(allergyId: String, nombreIngles: String?, manual: Boolean)
}
