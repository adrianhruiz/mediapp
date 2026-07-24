package com.medicapp.di

import android.content.Context
import com.medicapp.data.backup.BackupRepository
import com.medicapp.data.backup.FirestoreBackupRepository
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
import okhttp3.OkHttpClient

class AppContainer(context: Context) {

    private val networkModule = NetworkModule(context)

    val okHttpClient: OkHttpClient get() = networkModule.okHttpClient

    val authRepository: AuthRepository =
        FirebaseAuthRepository(FirebaseModule.auth, FirebaseModule.firestore)

    val medicationSearchRepository: MedicationSearchRepository =
        MedicationSearchRepository(networkModule.cimaApiService)

    val medicationCatalogRepository: MedicationCatalogRepository =
        MedicationCatalogRepository(networkModule.cimaApiService)

    val medicationHistoryRepository: MedicationHistoryRepository =
        FirestoreMedicationHistoryRepository(FirebaseModule.firestore, authRepository)

    val allergyRepository: AllergyRepository =
        FirestoreAllergyRepository(FirebaseModule.firestore, authRepository)

    val reasonRepository: ReasonRepository =
        FirestoreReasonRepository(FirebaseModule.firestore, authRepository)

    val scheduleRepository: ScheduleRepository =
        FirestoreScheduleRepository(FirebaseModule.firestore, authRepository)

    val backupRepository: BackupRepository =
        FirestoreBackupRepository(FirebaseModule.firestore, authRepository)
}
