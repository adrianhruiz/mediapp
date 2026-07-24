package com.medicapp.data.backup

/**
 * Decide qué hay que escribir de una copia y qué se deja como está.
 *
 * Está separado del repositorio a propósito: es la parte donde un despiste duplica datos del
 * usuario o se los pisa, y así se puede probar sin Firestore de por medio.
 */
object ImportPlanner {

    /** Identificadores que ya hay en la cuenta. */
    data class Existing(
        val allergyIds: Set<String> = emptySet(),
        val reasonIds: Set<String> = emptySet(),
        val takenMedicationIds: Set<String> = emptySet(),
        val scheduleIds: Set<String> = emptySet(),
        val doseIdsBySchedule: Map<String, Set<String>> = emptyMap(),
    )

    data class Plan(
        val allergies: List<BackupAllergy> = emptyList(),
        val reasons: List<BackupReason> = emptyList(),
        val takenMedications: List<BackupTakenEntry> = emptyList(),
        val schedules: List<BackupSchedule> = emptyList(),
        /** Dosis a crear, con el identificador de la pauta a la que cuelgan. */
        val doses: List<Pair<String, BackupDose>> = emptyList(),
        val skipped: Int = 0,
    ) {
        val added: Int
            get() = allergies.size + reasons.size + takenMedications.size + schedules.size + doses.size
    }

    fun plan(backup: BackupFile, existing: Existing): Plan {
        val allergies = backup.allergies.filterNot { it.id in existing.allergyIds }
        val reasons = backup.reasons.filterNot { it.id in existing.reasonIds }
        val takenMedications = backup.takenMedications.filterNot { it.id in existing.takenMedicationIds }
        val schedules = backup.schedules.filterNot { it.id in existing.scheduleIds }

        val doses = backup.schedules.flatMap { schedule ->
            val existingDoses = existing.doseIdsBySchedule[schedule.id].orEmpty()
            schedule.doses
                .filterNot { it.id in existingDoses }
                .map { schedule.id to it }
        }

        val plan = Plan(
            allergies = allergies,
            reasons = reasons,
            takenMedications = takenMedications,
            schedules = schedules,
            doses = doses,
        )
        return plan.copy(skipped = backup.totalItems - plan.added)
    }
}
