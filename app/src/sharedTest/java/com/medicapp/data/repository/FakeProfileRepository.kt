package com.medicapp.data.repository

import com.medicapp.model.PatientProfile
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeProfileRepository(initialProfile: PatientProfile? = null) : ProfileRepository {

    private val profileFlow = MutableStateFlow(initialProfile)

    override fun observe(): Flow<PatientProfile?> = profileFlow

    override suspend fun save(profile: PatientProfile) {
        profileFlow.value = profile
    }
}
