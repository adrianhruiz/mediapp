package com.medicapp.domain

object DoseScheduleCalculator {

    fun computeDoseTimes(startAtMillis: Long, intervalHours: Int, totalDoses: Int): List<Long> {
        val intervalMillis = intervalHours * 3_600_000L
        return (0 until totalDoses).map { index -> startAtMillis + index * intervalMillis }
    }

    /**
     * Hora de la última toma. Se guarda en la pauta para poder pedir a Firestore solo las pautas
     * que tocan un día o una semana, en vez de traerlas todas y mirar las fechas en el móvil.
     */
    fun endAtMillis(startAtMillis: Long, intervalHours: Int, totalDoses: Int): Long =
        startAtMillis + (totalDoses - 1).coerceAtLeast(0) * intervalHours * 3_600_000L
}
