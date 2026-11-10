package com.medicapp.data.backup

import com.medicapp.data.repository.FirestoreScheduleRepository
import com.medicapp.domain.DoseScheduleCalculator
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
    /** Pauta sin fin. Las copias anteriores no lo traen y se restauran como pautas con total. */
    val openEnded: Boolean = false,
)

/**
 * Por dónde va la pauta al restaurarla. Una pauta editada no acaba donde dice la fórmula, y una
 * sin fin no tiene fórmula: en las dos manda la última de sus tomas. Una sin fin necesita
 * además el número de esa toma para seguir numerando las que repone.
 */
fun BackupSchedule.progressFields(): Map<String, Any> = mapOf(
    FirestoreScheduleRepository.END_AT_FIELD to (
        doses.maxOfOrNull { it.scheduledAtMillis }
            ?: DoseScheduleCalculator.endAtMillis(startAtMillis, intervalHours, totalDoses)
        ),
    FirestoreScheduleRepository.OPEN_ENDED_FIELD to openEnded,
    FirestoreScheduleRepository.LAST_DOSE_NUMBER_FIELD to (doses.maxOfOrNull { it.doseNumber } ?: 0),
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
