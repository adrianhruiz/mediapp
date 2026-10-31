package com.medicapp.domain

import com.medicapp.data.repository.MedicationHistoryRepository
import com.medicapp.data.repository.ScheduleRepository
import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule

/**
 * Marcar una toma es siempre lo mismo: apuntarla en el historial y marcar la dosis con esa
 * entrada. Se hace desde Hoy, desde el detalle de la pauta y desde la notificación, así que vive
 * aquí y no repetido en cada sitio.
 *
 * Cancelar la alarma y la notificación queda en manos de quien llama, que es quien tiene el
 * contexto de Android.
 */
class DoseTaker(
    private val scheduleRepository: ScheduleRepository,
    private val medicationHistoryRepository: MedicationHistoryRepository,
) {

    /** Devuelve `false` sin escribir nada si la dosis ya no estaba pendiente. */
    suspend fun markTaken(schedule: MedicationSchedule, dose: MedicationDose): Boolean {
        if (dose.status != DoseStatus.PENDING) return false
        val historyEntryId = medicationHistoryRepository.add(
            nregistro = schedule.nregistro,
            nombreMedicamento = schedule.nombreMedicamento,
            reasonId = schedule.reasonId,
            reasonNombre = schedule.reasonNombre,
            dosisSnapshot = null,
            fotoUrl = schedule.fotoUrl,
        )
        scheduleRepository.markDoseTaken(schedule.id, dose.id, historyEntryId)
        return true
    }
}
