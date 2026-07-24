package com.medicapp.data.backup

import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.medicapp.data.repository.AuthRepository
import kotlinx.coroutines.tasks.await

interface BackupRepository {
    /** Vuelca todos los datos del usuario a una estructura serializable. */
    suspend fun exportAll(): BackupFile
}

class FirestoreBackupRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) : BackupRepository {

    private fun userDocument() = firestore.collection("users")
        .document(authRepository.currentUserId ?: error("No hay sesión iniciada"))

    override suspend fun exportAll(): BackupFile {
        val user = userDocument()
        return BackupFile(
            exportedAtMillis = nowMillis(),
            allergies = user.collection("allergies").get().await().documents.map { it.toBackupAllergy() },
            reasons = user.collection("reasons").get().await().documents.map { it.toBackupReason() },
            takenMedications = user.collection("takenMedications").get().await()
                .documents.map { it.toBackupTakenEntry() },
            schedules = user.collection("schedules").get().await().documents.map { scheduleDoc ->
                val doses = scheduleDoc.reference.collection("doses").get().await()
                    .documents.map { it.toBackupDose() }
                scheduleDoc.toBackupSchedule(doses)
            },
        )
    }
}

private fun DocumentSnapshot.millis(field: String): Long? = getTimestamp(field)?.toDate()?.time

private fun DocumentSnapshot.toBackupAllergy() = BackupAllergy(
    id = id,
    tipo = getString("tipo").orEmpty(),
    principioActivoId = getLong("principioActivoId")?.toInt(),
    principioActivoNombre = getString("principioActivoNombre"),
    atcCodigo = getString("atcCodigo"),
    atcNombre = getString("atcNombre"),
    createdAtMillis = millis("createdAt"),
)

private fun DocumentSnapshot.toBackupReason() = BackupReason(
    id = id,
    nombre = getString("nombre").orEmpty(),
    createdAtMillis = millis("createdAt"),
)

private fun DocumentSnapshot.toBackupTakenEntry() = BackupTakenEntry(
    id = id,
    nregistro = getString("nregistro").orEmpty(),
    nombreMedicamento = getString("nombreMedicamento").orEmpty(),
    reasonId = getString("reasonId").orEmpty(),
    reasonNombre = getString("reasonNombre").orEmpty(),
    timestampMillis = millis("timestamp"),
    dosisSnapshot = getString("dosisSnapshot"),
    fotoUrl = getString("fotoUrl"),
)

private fun DocumentSnapshot.toBackupSchedule(doses: List<BackupDose>) = BackupSchedule(
    id = id,
    nregistro = getString("nregistro").orEmpty(),
    nombreMedicamento = getString("nombreMedicamento").orEmpty(),
    fotoUrl = getString("fotoUrl"),
    intervalHours = (getLong("intervalHours") ?: 0L).toInt(),
    totalDoses = (getLong("totalDoses") ?: 0L).toInt(),
    startAtMillis = getLong("startAtMillis") ?: 0L,
    reasonId = getString("reasonId").orEmpty(),
    reasonNombre = getString("reasonNombre").orEmpty(),
    status = getString("status").orEmpty(),
    createdAtMillis = millis("createdAt"),
    doses = doses,
)

private fun DocumentSnapshot.toBackupDose() = BackupDose(
    id = id,
    doseNumber = (getLong("doseNumber") ?: 0L).toInt(),
    scheduledAtMillis = getLong("scheduledAtMillis") ?: 0L,
    status = getString("status").orEmpty(),
    takenAtMillis = getLong("takenAtMillis"),
    historyEntryId = getString("historyEntryId"),
)
