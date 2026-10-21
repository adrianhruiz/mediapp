package com.medicapp.ui.history

import com.medicapp.data.repository.ScheduledDose
import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/** Una toma de la semana; [missed] si su hora ya pasó y sigue sin marcar. */
data class WeekDose(
    val schedule: MedicationSchedule,
    val dose: MedicationDose,
    val missed: Boolean,
)

data class WeekDay(val date: LocalDate, val doses: List<WeekDose>, val isToday: Boolean)

/**
 * La semana de lunes a domingo. [due] cuenta las tomas cuya hora ya llegó (más las marcadas
 * antes de tiempo) y [taken] cuántas de ellas se tomaron; las saltadas al cerrar una pauta no
 * cuentan, porque nadie tenía que tomarlas.
 */
data class WeekPlan(
    val start: LocalDate,
    val days: List<WeekDay>,
    val taken: Int,
    val due: Int,
    val missed: List<WeekDose>,
) {
    val end: LocalDate get() = start.plusDays(6)
}

object WeekPlanner {

    /** El lunes de la semana de [nowMillis], desplazado [offsetWeeks] semanas. */
    fun weekStart(nowMillis: Long, zone: ZoneId, offsetWeeks: Int = 0): LocalDate =
        Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
            .with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
            .plusWeeks(offsetWeeks.toLong())

    /** Inicio y fin (excluido) de la semana que empieza en [start], en milisegundos. */
    fun range(start: LocalDate, zone: ZoneId): Pair<Long, Long> =
        start.atStartOfDay(zone).toInstant().toEpochMilli() to
            start.plusWeeks(1).atStartOfDay(zone).toInstant().toEpochMilli()

    fun plan(start: LocalDate, doses: List<ScheduledDose>, nowMillis: Long, zone: ZoneId): WeekPlan {
        val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
        val all = doses
            .sortedBy { it.dose.scheduledAtMillis }
            .map { (schedule, dose) ->
                WeekDose(
                    schedule = schedule,
                    dose = dose,
                    missed = dose.status == DoseStatus.PENDING && dose.scheduledAtMillis < nowMillis,
                )
            }
        val byDay = all.groupBy { Instant.ofEpochMilli(it.dose.scheduledAtMillis).atZone(zone).toLocalDate() }
        val days = (0L until 7L).map { offset ->
            val date = start.plusDays(offset)
            WeekDay(date = date, doses = byDay[date].orEmpty(), isToday = date == today)
        }
        val taken = all.count { it.dose.status == DoseStatus.TAKEN }
        val missed = all.filter { it.missed }
        return WeekPlan(start = start, days = days, taken = taken, due = taken + missed.size, missed = missed)
    }
}
