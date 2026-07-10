package com.medicapp.data.repository

import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule
import kotlinx.coroutines.flow.Flow

data class ScheduleWithDoses(val schedule: MedicationSchedule, val doses: List<MedicationDose>)

interface ScheduleRepository {

    fun getSchedules(): Flow<List<MedicationSchedule>>

    fun getDoses(scheduleId: String): Flow<List<MedicationDose>>

    suspend fun getSchedule(scheduleId: String): MedicationSchedule?

    suspend fun getDose(scheduleId: String, doseId: String): MedicationDose?

    suspend fun createSchedule(
        nregistro: String,
        nombreMedicamento: String,
        fotoUrl: String?,
        intervalHours: Int,
        totalDoses: Int,
        startAtMillis: Long,
        reasonId: String,
        reasonNombre: String,
    ): ScheduleWithDoses

    suspend fun markDoseTaken(scheduleId: String, doseId: String, historyEntryId: String)

    suspend fun getPendingDoses(scheduleId: String): List<MedicationDose>

    suspend fun closeSchedule(scheduleId: String)

    suspend fun getActiveSchedulesWithPendingDoses(): List<ScheduleWithDoses>
}
