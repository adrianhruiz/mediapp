package com.medicapp.domain

import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose

/** Una toma nueva que crea la edición. */
data class PlannedDose(val doseNumber: Int, val scheduledAtMillis: Long)

/** Cómo queda una pauta tras editarla. */
data class ScheduleEditPlan(
    /** Hora de la primera toma, hecha o por hacer: la pauta se pinta en los días desde ahí. */
    val startAtMillis: Long,
    /**
     * Hora de la última toma. Tras editar ya no sale de la fórmula de la pauta (las tomas hechas
     * conservan su hora y las nuevas siguen otro ritmo), así que hay que guardarla tal cual.
     */
    val endAtMillis: Long,
    /** Tomas que se conservan y cambian de número al reordenarlas por hora. */
    val renumberedKeptDoses: Map<String, Int>,
    val newDoses: List<PlannedDose>,
)

/**
 * Editar una pauta conserva las tomas ya hechas tal cual y sustituye todas las pendientes por
 * las que faltan hasta el nuevo total, empezando en la hora de la próxima toma con la nueva
 * frecuencia. Una toma pendiente que se pasó sin marcar también se sustituye: sigue faltando
 * tomarla, así que pasa a contar entre las nuevas.
 */
object ScheduleEditPlanner {

    /** Tomas que la edición no toca: las que ya no están pendientes. */
    fun keptDoseCount(doses: List<MedicationDose>): Int = doses.count { it.status != DoseStatus.PENDING }

    /** Devuelve null si los valores no son válidos: el total no deja ninguna toma por hacer. */
    fun plan(
        doses: List<MedicationDose>,
        intervalHours: Int,
        totalDoses: Int,
        nextDoseAtMillis: Long,
    ): ScheduleEditPlan? {
        if (intervalHours < 1) return null
        val kept = doses.filter { it.status != DoseStatus.PENDING }
        val remaining = totalDoses - kept.size
        if (remaining < 1) return null

        val newTimes = DoseScheduleCalculator.computeDoseTimes(nextDoseAtMillis, intervalHours, remaining)
        // Las conservadas van delante: a igual hora, la ya hecha cuenta primero.
        val timeline = (kept.map { it.scheduledAtMillis to it } + newTimes.map { it to null })
            .sortedBy { it.first }

        val renumbered = mutableMapOf<String, Int>()
        val newDoses = mutableListOf<PlannedDose>()
        timeline.forEachIndexed { index, (time, keptDose) ->
            val number = index + 1
            when {
                keptDose == null -> newDoses += PlannedDose(number, time)
                keptDose.doseNumber != number -> renumbered[keptDose.id] = number
            }
        }
        return ScheduleEditPlan(
            startAtMillis = timeline.first().first,
            endAtMillis = timeline.last().first,
            renumberedKeptDoses = renumbered,
            newDoses = newDoses,
        )
    }
}
