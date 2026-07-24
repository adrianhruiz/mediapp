package com.medicapp.data.backup

import kotlinx.serialization.Serializable

/** Sube si el formato deja de poder leerse con el código de una versión anterior. */
const val BACKUP_FORMAT_VERSION = 1

/**
 * Copia de los datos del usuario tal y como están guardados en Firestore, no tal y como los
 * ve la app.
 *
 * La diferencia importa: los modelos de dominio se dejan por el camino `createdAt`, y
 * Firestore **excluye de una consulta ordenada los documentos que no tienen el campo de
 * ordenación**. Una pauta restaurada sin `createdAt` no aparecería en la lista de
 * recordatorios, que ordena por ese campo. Por eso aquí se guardan los campos crudos.
 *
 * Los identificadores originales se conservan porque hay referencias entre colecciones: una
 * pauta apunta a su motivo y una toma apunta a su entrada de historial. Regenerarlos al
 * restaurar rompería esos vínculos.
 */
@Serializable
data class BackupFile(
    val version: Int = BACKUP_FORMAT_VERSION,
    val exportedAtMillis: Long,
    val allergies: List<BackupAllergy> = emptyList(),
    val reasons: List<BackupReason> = emptyList(),
    val takenMedications: List<BackupTakenEntry> = emptyList(),
    val schedules: List<BackupSchedule> = emptyList(),
) {
    val totalItems: Int
        get() = allergies.size + reasons.size + takenMedications.size +
            schedules.size + schedules.sumOf { it.doses.size }
}

@Serializable
data class BackupAllergy(
    val id: String,
    val tipo: String,
    val principioActivoId: Int? = null,
    val principioActivoNombre: String? = null,
    val atcCodigo: String? = null,
    val atcNombre: String? = null,
    val createdAtMillis: Long? = null,
)

@Serializable
data class BackupReason(
    val id: String,
    val nombre: String,
    val createdAtMillis: Long? = null,
)

@Serializable
data class BackupTakenEntry(
    val id: String,
    val nregistro: String,
    val nombreMedicamento: String,
    val reasonId: String,
    val reasonNombre: String,
    val timestampMillis: Long? = null,
    val dosisSnapshot: String? = null,
    val fotoUrl: String? = null,
)

@Serializable
data class BackupSchedule(
    val id: String,
    val nregistro: String,
    val nombreMedicamento: String,
    val fotoUrl: String? = null,
    val intervalHours: Int,
    val totalDoses: Int,
    val startAtMillis: Long,
    val reasonId: String,
    val reasonNombre: String,
    val status: String,
    val createdAtMillis: Long? = null,
    val doses: List<BackupDose> = emptyList(),
)

@Serializable
data class BackupDose(
    val id: String,
    val doseNumber: Int,
    val scheduledAtMillis: Long,
    val status: String,
    val takenAtMillis: Long? = null,
    val historyEntryId: String? = null,
)
