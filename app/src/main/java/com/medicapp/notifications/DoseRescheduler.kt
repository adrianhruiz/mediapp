package com.medicapp.notifications

import android.content.Context
import android.util.Log
import com.medicapp.data.repository.ScheduleRepository
import kotlinx.coroutines.CancellationException

/**
 * Vuelve a poner las alarmas de las dosis que siguen pendientes y aún no han pasado.
 *
 * Hace falta cuando las alarmas del sistema no existen pero las pautas sí: al reiniciar el
 * móvil, al restaurar una copia de seguridad y al entrar en la cuenta en un móvil nuevo o tras
 * reinstalar (ver [SessionDoseRescheduler]). Sin esto, la pauta se vería en la lista pero no
 * avisaría nunca.
 *
 * Antes se reponen las pautas sin fin, para que la ventana de tomas por delante siga cubierta
 * aunque no se haya marcado ninguna en días; las tomas nuevas salen ya entre las pendientes y
 * reciben su alarma aquí mismo.
 */
object DoseRescheduler {

    suspend fun rescheduleFutureDoses(
        context: Context,
        scheduleRepository: ScheduleRepository,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val appContext = context.applicationContext
        try {
            scheduleRepository.replenishOpenEndedSchedules(nowMillis)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Sin conexión no se repone, pero las alarmas de lo que ya existe se ponen igual.
            Log.w("DoseRescheduler", "Could not replenish open-ended schedules", e)
        }
        scheduleRepository.getActiveSchedulesWithPendingDoses().forEach { scheduleWithDoses ->
            scheduleWithDoses.doses
                .filter { it.scheduledAtMillis > nowMillis }
                .forEach { dose ->
                    DoseAlarmScheduler.scheduleDose(
                        appContext,
                        scheduleWithDoses.schedule.id,
                        dose,
                        scheduleWithDoses.schedule.nombreMedicamento,
                        scheduleWithDoses.schedule.totalDoses,
                    )
                }
        }
    }
}
