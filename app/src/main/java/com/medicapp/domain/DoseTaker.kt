package com.medicapp.domain

import android.util.Log
import com.medicapp.data.repository.MedicationHistoryRepository
import com.medicapp.data.repository.ScheduleRepository
import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule
import kotlinx.coroutines.CancellationException

/**
 * Marcar una toma es siempre lo mismo: apuntarla en el historial y marcar la dosis con esa
 * entrada. Se hace desde Hoy, desde el detalle de la pauta y desde la notificación, así que vive
 * aquí y no repetido en cada sitio.
 *
 * Cancelar la alarma y la notificación queda en manos de quien llama, que es quien tiene el
 * contexto de Android.
 *
 * En una pauta sin fin, marcar una toma repone la ventana de tomas por delante, y las nuevas
 * reciben alarma con [scheduleAlarms] como las demás. Va aquí y no en cada pantalla para que la
 * notificación, Hoy y el detalle repongan igual.
 */
class DoseTaker(
    private val scheduleRepository: ScheduleRepository,
    private val medicationHistoryRepository: MedicationHistoryRepository,
    private val now: () -> Long = System::currentTimeMillis,
    private val scheduleAlarms: (MedicationSchedule, List<MedicationDose>) -> Unit = { _, _ -> },
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
        if (schedule.openEnded) replenish(schedule)
        return true
    }

    /**
     * Reponer necesita conexión (va en transacción). Si falla, la toma ya está marcada y la
     * ventana se repone en el siguiente arranque de la app o de sesión.
     */
    private suspend fun replenish(schedule: MedicationSchedule) {
        val added = try {
            scheduleRepository.replenishOpenEndedDoses(schedule.id, now())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w("DoseTaker", "Could not replenish open-ended schedule", e)
            return
        }
        if (added.isNotEmpty()) scheduleAlarms(schedule, added)
    }
}
