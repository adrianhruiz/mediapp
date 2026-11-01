package com.medicapp.data.repository

import com.medicapp.model.PatientProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeProfileRepository(initialProfile: PatientProfile? = null) : ProfileRepository {

    private val profileFlow = MutableStateFlow(initialProfile)

    /** Hace fallar el siguiente guardado, como sin permiso o con Firestore caído. */
    var failNextSave = false

    override fun observe(): Flow<PatientProfile?> = profileFlow

    override suspend fun save(profile: PatientProfile) {
        if (failNextSave) {
            failNextSave = false
            error("Fallo simulado al guardar el perfil")
        }
        profileFlow.value = profile
    }
}
