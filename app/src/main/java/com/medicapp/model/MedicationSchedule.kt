package com.medicapp.model

data class MedicationSchedule(
    val id: String,
    val nregistro: String,
    val nombreMedicamento: String,
    val fotoUrl: String?,
    val intervalHours: Int,
    val totalDoses: Int,
    val startAtMillis: Long,
    val reasonId: String,
    val reasonNombre: String,
    val status: ScheduleStatus,
    /**
     * Pauta sin fin: no tiene total ([totalDoses] vale 0) y dura hasta que se cierra a mano. Sus
     * tomas se van creando por delante en una ventana de días (ver
     * `DoseScheduleCalculator.openEndedWindow`).
     */
    val openEnded: Boolean = false,
)
