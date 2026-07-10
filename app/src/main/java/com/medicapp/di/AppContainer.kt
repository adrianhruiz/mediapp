package com.medicapp.di

import com.medicapp.data.remote.NetworkModule
import com.medicapp.data.remote.firebase.FirebaseModule
import com.medicapp.data.repository.AllergyRepository
import com.medicapp.data.repository.AuthRepository
import com.medicapp.data.repository.FirebaseAuthRepository
import com.medicapp.data.repository.FirestoreAllergyRepository
import com.medicapp.data.repository.FirestoreMedicationHistoryRepository
import com.medicapp.data.repository.FirestoreReasonRepository
import com.medicapp.data.repository.FirestoreScheduleRepository
import com.medicapp.data.repository.MedicationCatalogRepository
import com.medicapp.data.repository.MedicationHistoryRepository
import com.medicapp.data.repository.MedicationSearchRepository
import com.medicapp.data.repository.ReasonRepository
import com.medicapp.data.repository.ScheduleRepository

class AppContainer {

    val authRepository: AuthRepository =
        FirebaseAuthRepository(FirebaseModule.auth, FirebaseModule.firestore)

    val medicationSearchRepository: MedicationSearchRepository =
        MedicationSearchRepository(NetworkModule.cimaApiService)

    val medicationCatalogRepository: MedicationCatalogRepository =
        MedicationCatalogRepository(NetworkModule.cimaApiService)

    val medicationHistoryRepository: MedicationHistoryRepository =
        FirestoreMedicationHistoryRepository(FirebaseModule.firestore, authRepository)

    val allergyRepository: AllergyRepository =
        FirestoreAllergyRepository(FirebaseModule.firestore, authRepository)

    val reasonRepository: ReasonRepository =
        FirestoreReasonRepository(FirebaseModule.firestore, authRepository)

    val scheduleRepository: ScheduleRepository =
        FirestoreScheduleRepository(FirebaseModule.firestore, authRepository)
}
