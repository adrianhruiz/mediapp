package com.medicapp.model

data class MedicationDose(
    val id: String,
    val scheduleId: String,
    val doseNumber: Int,
    val scheduledAtMillis: Long,
    val status: DoseStatus,
    val takenAtMillis: Long?,
    val historyEntryId: String?,
)
