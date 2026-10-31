package com.medicapp.ui.today

import com.medicapp.data.repository.ScheduledDose
import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule

/** Una toma de hoy tal como se pinta: [overdue] si su hora ya pasó y sigue sin marcar. */
data class TodayDose(
    val schedule: MedicationSchedule,
    val dose: MedicationDose,
    val overdue: Boolean,
) {
    val pending: Boolean get() = dose.status == DoseStatus.PENDING
}

/** Las tomas del día y la que toca ahora: la pendiente más temprana, atrasada o no. */
data class TodayPlan(
    val doses: List<TodayDose> = emptyList(),
    val next: TodayDose? = null,
) {
    val allDone: Boolean get() = doses.isNotEmpty() && next == null
}

object TodayPlanner {

    fun plan(doses: List<ScheduledDose>, nowMillis: Long): TodayPlan {
        val today = doses
            .sortedBy { it.dose.scheduledAtMillis }
            .map { (schedule, dose) ->
                TodayDose(
                    schedule = schedule,
                    dose = dose,
                    overdue = dose.status == DoseStatus.PENDING && dose.scheduledAtMillis < nowMillis,
                )
            }
        return TodayPlan(doses = today, next = today.firstOrNull { it.pending })
    }
}
