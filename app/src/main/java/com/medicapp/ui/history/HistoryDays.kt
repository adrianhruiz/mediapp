package com.medicapp.ui.history

import com.medicapp.model.TakenEntry
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Un día del historial con su etiqueta («Hoy», «Ayer», «Martes, 3 de noviembre») y sus entradas. */
data class DayGroup(val day: LocalDate, val label: String, val entries: List<TakenEntry>)

/**
 * Agrupa el historial por día. Con meses enteros cada fila tenía que llevar su fecha completa;
 * con el día en la cabecera, a la fila le basta la hora y el motivo.
 */
object HistoryDays {

    private val spanish = Locale.forLanguageTag("es-ES")
    private val thisYear = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", spanish)
    private val otherYear = DateTimeFormatter.ofPattern("d 'de' MMMM 'de' yyyy", spanish)
    private val hour = DateTimeFormatter.ofPattern("HH:mm", spanish)

    /** Respeta el orden recibido: el repositorio ya da las entradas de la más reciente a la más antigua. */
    fun group(entries: List<TakenEntry>, today: LocalDate, zone: ZoneId = ZoneId.systemDefault()): List<DayGroup> =
        entries
            .groupBy { Instant.ofEpochMilli(it.timestampMillis).atZone(zone).toLocalDate() }
            .map { (day, dayEntries) -> DayGroup(day, label(day, today), dayEntries) }

    /** La segunda línea de la fila: «08:05 · infección de garganta». */
    fun note(entry: TakenEntry, zone: ZoneId = ZoneId.systemDefault()): String =
        "${hour.format(Instant.ofEpochMilli(entry.timestampMillis).atZone(zone))} · ${entry.reasonNombre}"

    private fun label(day: LocalDate, today: LocalDate): String = when {
        day == today -> "Hoy"
        day == today.minusDays(1) -> "Ayer"
        day.year == today.year -> thisYear.format(day).replaceFirstChar { it.titlecase(spanish) }
        else -> otherYear.format(day)
    }
}
