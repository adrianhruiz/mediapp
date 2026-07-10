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
)
