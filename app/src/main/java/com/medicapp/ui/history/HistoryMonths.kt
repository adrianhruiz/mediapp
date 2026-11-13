package com.medicapp.ui.history

import com.medicapp.model.TakenEntry
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.TextStyle
import java.util.Locale

/** Un mes del historial con su etiqueta («Octubre 2026») y sus entradas, en el orden recibido. */
data class MonthGroup(val month: YearMonth, val label: String, val entries: List<TakenEntry>)

object HistoryMonths {

    private val spanish = Locale.forLanguageTag("es-ES")

    /**
     * Agrupa las entradas por mes respetando el orden en que llegan (el repositorio ya las da de
     * la más reciente a la más antigua), así que los meses salen en ese mismo orden.
     */
    fun group(entries: List<TakenEntry>, zone: ZoneId = ZoneId.systemDefault()): List<MonthGroup> =
        entries
            .groupBy { YearMonth.from(Instant.ofEpochMilli(it.timestampMillis).atZone(zone)) }
            .map { (month, monthEntries) -> MonthGroup(month, label(month), monthEntries) }

    private fun label(month: YearMonth): String {
        val name = month.month.getDisplayName(TextStyle.FULL_STANDALONE, spanish)
        return "${name.replaceFirstChar { it.titlecase(spanish) }} ${month.year}"
    }
}
