package com.medicapp.notifications

import android.content.Context
import com.medicapp.data.repository.ScheduleRepository

/**
 * Vuelve a poner las alarmas de las dosis que siguen pendientes y aún no han pasado.
 *
 * Hace falta cuando las alarmas del sistema no existen pero las pautas sí: al reiniciar el
 * móvil, al restaurar una copia de seguridad y al entrar en la cuenta en un móvil nuevo o tras
 * reinstalar (ver [SessionDoseRescheduler]). Sin esto, la pauta se vería en la lista pero no
 * avisaría nunca.
 */
object DoseRescheduler {

    suspend fun rescheduleFutureDoses(
        context: Context,
        scheduleRepository: ScheduleRepository,
        nowMillis: Long = System.currentTimeMillis(),
    ) {
        val appContext = context.applicationContext
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
