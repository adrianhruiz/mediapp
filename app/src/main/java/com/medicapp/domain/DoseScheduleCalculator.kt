package com.medicapp.domain

object DoseScheduleCalculator {

    fun computeDoseTimes(startAtMillis: Long, intervalHours: Int, totalDoses: Int): List<Long> {
        val intervalMillis = intervalHours * 3_600_000L
        return (0 until totalDoses).map { index -> startAtMillis + index * intervalMillis }
    }
}
