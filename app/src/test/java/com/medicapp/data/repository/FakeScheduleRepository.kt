package com.medicapp.data.repository

import com.medicapp.domain.DoseScheduleCalculator
import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule
import com.medicapp.model.ScheduleStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeScheduleRepository : ScheduleRepository {

    private val schedulesFlow = MutableStateFlow<List<MedicationSchedule>>(emptyList())
    private val dosesByScheduleFlow = mutableMapOf<String, MutableStateFlow<List<MedicationDose>>>()
    private var nextScheduleId = 1
    private var nextDoseId = 1

    private fun dosesFlow(scheduleId: String) =
        dosesByScheduleFlow.getOrPut(scheduleId) { MutableStateFlow(emptyList()) }

    val schedules: List<MedicationSchedule> get() = schedulesFlow.value

    fun dosesSnapshot(scheduleId: String): List<MedicationDose> = dosesFlow(scheduleId).value

    override fun getSchedules(): Flow<List<MedicationSchedule>> = schedulesFlow

    override fun getDoses(scheduleId: String): Flow<List<MedicationDose>> = dosesFlow(scheduleId)

    override suspend fun getSchedule(scheduleId: String): MedicationSchedule? =
        schedulesFlow.value.firstOrNull { it.id == scheduleId }

    override suspend fun getDose(scheduleId: String, doseId: String): MedicationDose? =
        dosesFlow(scheduleId).value.firstOrNull { it.id == doseId }

    override suspend fun createSchedule(
        nregistro: String,
        nombreMedicamento: String,
        fotoUrl: String?,
        intervalHours: Int,
        totalDoses: Int,
        startAtMillis: Long,
        reasonId: String,
        reasonNombre: String,
    ): ScheduleWithDoses {
        val scheduleId = "schedule-${nextScheduleId++}"
        val doseTimes = DoseScheduleCalculator.computeDoseTimes(startAtMillis, intervalHours, totalDoses)
        val doses = doseTimes.mapIndexed { index, scheduledAtMillis ->
            MedicationDose(
                id = "dose-${nextDoseId++}",
                scheduleId = scheduleId,
                doseNumber = index + 1,
                scheduledAtMillis = scheduledAtMillis,
                status = DoseStatus.PENDING,
                takenAtMillis = null,
                historyEntryId = null,
            )
        }
        val schedule = MedicationSchedule(
            id = scheduleId,
            nregistro = nregistro,
            nombreMedicamento = nombreMedicamento,
            fotoUrl = fotoUrl,
            intervalHours = intervalHours,
            totalDoses = totalDoses,
            startAtMillis = startAtMillis,
            reasonId = reasonId,
            reasonNombre = reasonNombre,
            status = ScheduleStatus.ACTIVE,
        )
        schedulesFlow.value = schedulesFlow.value + schedule
        dosesFlow(scheduleId).value = doses
        return ScheduleWithDoses(schedule, doses)
    }

    override suspend fun markDoseTaken(scheduleId: String, doseId: String, historyEntryId: String) {
        val updated = dosesFlow(scheduleId).value.map { dose ->
            if (dose.id == doseId) {
                dose.copy(status = DoseStatus.TAKEN, takenAtMillis = 0L, historyEntryId = historyEntryId)
            } else {
                dose
            }
        }
        dosesFlow(scheduleId).value = updated
        if (updated.none { it.status == DoseStatus.PENDING }) {
            closeScheduleStatusOnly(scheduleId)
        }
    }

    override suspend fun getPendingDoses(scheduleId: String): List<MedicationDose> =
        dosesFlow(scheduleId).value.filter { it.status == DoseStatus.PENDING }

    override suspend fun closeSchedule(scheduleId: String) {
        dosesFlow(scheduleId).value = dosesFlow(scheduleId).value.map { dose ->
            if (dose.status == DoseStatus.PENDING) dose.copy(status = DoseStatus.SKIPPED) else dose
        }
        closeScheduleStatusOnly(scheduleId)
    }

    override suspend fun getActiveSchedulesWithPendingDoses(): List<ScheduleWithDoses> =
        schedulesFlow.value
            .filter { it.status == ScheduleStatus.ACTIVE }
            .map { schedule -> ScheduleWithDoses(schedule, getPendingDoses(schedule.id)) }

    private fun closeScheduleStatusOnly(scheduleId: String) {
        schedulesFlow.value = schedulesFlow.value.map {
            if (it.id == scheduleId) it.copy(status = ScheduleStatus.CLOSED) else it
        }
    }
}
