package com.medicapp.data.repository

import com.medicapp.model.PatientProfile
import kotlinx.coroutines.flow.Flow

interface ProfileRepository {

    /** El perfil del paciente en tiempo real, o null si todavía no lo ha rellenado. */
    fun observe(): Flow<PatientProfile?>

    suspend fun save(profile: PatientProfile)
}
