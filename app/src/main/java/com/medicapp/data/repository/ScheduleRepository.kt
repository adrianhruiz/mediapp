package com.medicapp.data.repository

import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule
import kotlinx.coroutines.flow.Flow

data class ScheduleWithDoses(val schedule: MedicationSchedule, val doses: List<MedicationDose>)

interface ScheduleRepository {

    fun getSchedulesPage(limit: Long): Flow<PagedResult<MedicationSchedule>>

    /** Números de registro con pauta activa, para marcarlos como guardados en la búsqueda. */
    fun getActiveScheduleNregistros(): Flow<Set<String>>

    fun getDoses(scheduleId: String): Flow<List<MedicationDose>>

    /** Una única pauta en tiempo real, sin traerse el resto de la colección. */
    fun observeSchedule(scheduleId: String): Flow<MedicationSchedule?>

    suspend fun getSchedule(scheduleId: String): MedicationSchedule?

    /** Si ya hay una pauta activa para este medicamento, para avisar del duplicado. */
    suspend fun hasActiveScheduleFor(nregistro: String): Boolean

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
