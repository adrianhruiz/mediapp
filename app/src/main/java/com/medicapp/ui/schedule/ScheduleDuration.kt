package com.medicapp.ui.schedule

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Traduce «durante N días» al número de tomas que se guarda en la pauta. En Firestore sigue
 * viviendo solo el total de tomas; los días son una forma más natural de pedirlo.
 */
object ScheduleDuration {

    /** Tope del contador de días: más allá ya no es un tratamiento, es una pauta crónica. */
    const val MAX_DAYS = 365

    private val lastDoseFormat = DateTimeFormatter.ofPattern("d MMM HH:mm", Locale.forLanguageTag("es-ES"))

    /**
     * Tomas que caben en [days] días tomando una cada [intervalHours] horas, contando la primera
     * en la hora de inicio: son todas las que *empiezan* dentro de la ventana de `days × 24` horas,
     * es decir, `ceil(days × 24 / intervalHours)`.
     *
     * Con los intervalos que dividen 24 (8, 12, 24 h) sale justo `días × tomas al día`. Con
     * «Otra» y un intervalo que no divide 24 se redondea hacia arriba: cada 10 h durante 2 días
     * son 5 tomas (0, 10, 20, 30 y 40 h), porque la de las 40 h aún cae dentro del segundo día.
     * Si el intervalo es más largo que la ventana queda una sola toma, la primera.
     */
    fun totalDoses(days: Int, intervalHours: Int): Int {
        require(days >= 1) { "days debe ser al menos 1" }
        require(intervalHours >= 1) { "intervalHours debe ser al menos 1" }
        val windowHours = days * 24
        return (windowHours + intervalHours - 1) / intervalHours
    }

    /** Resumen en una línea: «21 tomas · última el 7 nov 21:32». */
    fun summary(totalDoses: Int, lastDoseMillis: Long, zone: ZoneId = ZoneId.systemDefault()): String {
        val at = lastDoseFormat.format(Instant.ofEpochMilli(lastDoseMillis).atZone(zone)).replace(".", "")
        return if (totalDoses == 1) "1 toma · el $at" else "$totalDoses tomas · última el $at"
    }
}
