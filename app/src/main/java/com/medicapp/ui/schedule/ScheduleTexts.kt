package com.medicapp.ui.schedule

import com.medicapp.domain.DoseInterval
import com.medicapp.model.MedicationSchedule

/**
 * Textos de una pauta que cambian si es sin fin: no tiene total que contar ni periodo que
 * terminar, así que en lugar de "N tomas" se dice que dura hasta que se cierre.
 */
object ScheduleTexts {

    private const val UNTIL_CLOSED = "hasta que la cierres"

    /** Línea de la tarjeta en la lista de pautas: "Cada día · 10 tomas". */
    fun listSummary(schedule: MedicationSchedule): String {
        val interval = DoseInterval.label(schedule.intervalHours)
        return if (schedule.openEnded) "$interval · $UNTIL_CLOSED" else "$interval · ${schedule.totalDoses} tomas"
    }

    /** Línea del detalle de la pauta: "Cada día · 3/10 tomas". */
    fun detailSummary(schedule: MedicationSchedule, takenCount: Int): String {
        val interval = DoseInterval.label(schedule.intervalHours)
        return if (schedule.openEnded) {
            "$interval · $UNTIL_CLOSED"
        } else {
            "$interval · $takenCount/${schedule.totalDoses} tomas"
        }
    }

    /** Una pauta sin fin no tiene periodo: lo que se cierra es la pauta. */
    fun closeLabel(schedule: MedicationSchedule): String =
        if (schedule.openEnded) "Cerrar pauta" else "Cerrar periodo"
}
