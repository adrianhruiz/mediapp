package com.medicapp.ui.today

import com.medicapp.model.DoseStatus
import com.medicapp.ui.components.MedicationNames
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

// Los textos de la línea del día, fuera de la pantalla para poder probarlos sin Compose.

internal val spanish: Locale = Locale.forLanguageTag("es-ES")
private val hourFormat = DateTimeFormatter.ofPattern("HH:mm", spanish)
private val shortDayFormat = DateTimeFormatter.ofPattern("d MMM", spanish)
private val dateFormat = DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", spanish)

internal fun Long.hour(): String = hourFormat.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

/** "ayer", "el 20 oct"... para una toma de otro día; null si es de hoy. */
internal fun Long.earlierDayText(nowMillis: Long): String? {
    val zone = ZoneId.systemDefault()
    val day = Instant.ofEpochMilli(this).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    return when {
        !day.isBefore(today) -> null
        day == today.minusDays(1) -> "ayer"
        else -> "el " + shortDayFormat.format(day).replace(".", "")
    }
}

/**
 * La fecha en una frase normal, «Martes, 11 de noviembre»: antes iba en mayúsculas y mono,
 * y competía con el título en vez de acompañarlo.
 */
internal fun dateLabel(nowMillis: Long): String =
    dateFormat.format(Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault()))
        .replaceFirstChar { it.titlecase(spanish) }

internal fun TodayDose.statusText(): String = when {
    dose.status == DoseStatus.TAKEN -> "Tomada"
    dose.status == DoseStatus.SKIPPED -> "Saltada"
    overdue -> "Sin tomar"
    else -> "Pendiente"
}

/** La frase de la tarjeta abierta: cuándo toca, o qué pasó con esta toma. */
internal fun TodayDose.whenText(nowMillis: Long): String {
    val at = dose.scheduledAtMillis
    return when (dose.status) {
        DoseStatus.TAKEN -> dose.takenAtMillis?.let { "Tomada a las ${it.hour()}" } ?: "Tomada"
        DoseStatus.SKIPPED -> "Saltada al cerrar la pauta"
        DoseStatus.PENDING -> {
            val minutes = (at - nowMillis) / 60_000
            when {
                overdue -> at.earlierDayText(nowMillis)
                    ?.let { "Sin tomar desde $it a las ${at.hour()}" }
                    ?: "Te toca desde las ${at.hour()}"
                minutes < 1 -> "Te toca ahora"
                minutes < 60 -> "Toca en $minutes min"
                else -> "Toca a las ${at.hour()}"
            }
        }
    }
}

/**
 * La segunda línea de una fila cerrada. Lo que está por venir no lleva nada: la hora ya está a
 * la izquierda y repetirla solo añadía ruido.
 */
internal fun TodayDose.timelineNote(nowMillis: Long): String? = when {
    dose.status == DoseStatus.PENDING && !overdue -> null
    // De otro día: la fecha ya va en la columna de la hora, no se repite aquí.
    carriedOver && dose.status == DoseStatus.PENDING -> "Sin tomar"
    else -> whenText(nowMillis)
}

/** La hora de la fila; si la toma es de otro día, con el día encima («ayer», «21 oct»). */
internal fun TodayDose.timelineHour(nowMillis: Long): String {
    val at = dose.scheduledAtMillis
    return at.earlierDayText(nowMillis)?.let { "${it.removePrefix("el ")}\n${at.hour()}" } ?: at.hour()
}

/** «20 mg · para la acidez»: dosis y motivo en una línea, sin la etiqueta «Para:». */
internal fun TodayDose.detailLine(): String {
    val detail = MedicationNames.split(schedule.nombreMedicamento).second
    val reason = "para ${schedule.reasonNombre}"
    return detail?.let { "$it · $reason" } ?: reason.replaceFirstChar { it.titlecase(spanish) }
}
