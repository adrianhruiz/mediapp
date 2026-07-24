package com.medicapp.data.backup

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.medicapp.data.repository.AuthRepository
import kotlinx.coroutines.tasks.await
import java.util.Date

/** Qué se ha restaurado y qué se ha dejado como estaba. */
data class ImportResult(val added: Int, val skipped: Int)

/** La copia viene de una versión posterior de la app y no sabemos leerla con garantías. */
class UnsupportedBackupVersionException(val version: Int) :
    Exception("Copia en formato $version, esta versión solo entiende hasta $BACKUP_FORMAT_VERSION")

interface BackupRepository {
    /** Vuelca todos los datos del usuario a una estructura serializable. */
    suspend fun exportAll(): BackupFile

    /**
     * Restaura lo que falte, conservando los identificadores originales para no romper las
     * referencias entre colecciones. Lo que ya existe **se deja intacto**: importar dos veces
     * la misma copia no duplica nada, y restaurar una copia vieja no pisa lo más reciente.
     */
    suspend fun import(backup: BackupFile): ImportResult
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

    override suspend fun import(backup: BackupFile): ImportResult {
        if (backup.version > BACKUP_FORMAT_VERSION) throw UnsupportedBackupVersionException(backup.version)

        val user = userDocument()
        val plan = ImportPlanner.plan(backup, readExistingIds(backup))

        val writes = buildList {
            plan.allergies.forEach { add(user.collection("allergies").document(it.id) to it.toFirestore()) }
            plan.reasons.forEach { add(user.collection("reasons").document(it.id) to it.toFirestore()) }
            plan.takenMedications.forEach {
                add(user.collection("takenMedications").document(it.id) to it.toFirestore())
            }
            plan.schedules.forEach { add(user.collection("schedules").document(it.id) to it.toFirestore()) }
            plan.doses.forEach { (scheduleId, dose) ->
                add(
                    user.collection("schedules").document(scheduleId)
                        .collection("doses").document(dose.id) to dose.toFirestore(),
                )
            }
        }

        // Un lote de Firestore admite 500 operaciones como mucho.
        writes.chunked(FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (reference, data) -> batch.set(reference, data) }
            batch.commit().await()
        }

        return ImportResult(added = plan.added, skipped = plan.skipped)
    }

    private suspend fun readExistingIds(backup: BackupFile): ImportPlanner.Existing {
        val user = userDocument()
        suspend fun idsIn(collection: String): Set<String> =
            user.collection(collection).get().await().documents.map { it.id }.toSet()

        val scheduleIds = idsIn("schedules")
        // Solo miramos las dosis de las pautas que ya existen: si la pauta no está, sus dosis tampoco.
        val doseIdsBySchedule = backup.schedules
            .map { it.id }
            .filter { it in scheduleIds }
            .associateWith { scheduleId ->
                user.collection("schedules").document(scheduleId)
                    .collection("doses").get().await().documents.map { it.id }.toSet()
            }

        return ImportPlanner.Existing(
            allergyIds = idsIn("allergies"),
            reasonIds = idsIn("reasons"),
            takenMedicationIds = idsIn("takenMedications"),
            scheduleIds = scheduleIds,
            doseIdsBySchedule = doseIdsBySchedule,
        )
    }
}

private const val FIRESTORE_BATCH_LIMIT = 500

private fun Long?.asTimestamp(): Timestamp? = this?.let { Timestamp(Date(it)) }

private fun BackupAllergy.toFirestore() = mapOf(
    "tipo" to tipo,
    "principioActivoId" to principioActivoId,
    "principioActivoNombre" to principioActivoNombre,
    "atcCodigo" to atcCodigo,
    "atcNombre" to atcNombre,
    "createdAt" to createdAtMillis.asTimestamp(),
)

private fun BackupReason.toFirestore() = mapOf(
    "nombre" to nombre,
    "createdAt" to createdAtMillis.asTimestamp(),
)

private fun BackupTakenEntry.toFirestore() = mapOf(
    "nregistro" to nregistro,
    "nombreMedicamento" to nombreMedicamento,
    "reasonId" to reasonId,
    "reasonNombre" to reasonNombre,
    "dosisSnapshot" to dosisSnapshot,
    "fotoUrl" to fotoUrl,
    "timestamp" to timestampMillis.asTimestamp(),
)

private fun BackupSchedule.toFirestore() = mapOf(
    "nregistro" to nregistro,
    "nombreMedicamento" to nombreMedicamento,
    "fotoUrl" to fotoUrl,
    "intervalHours" to intervalHours,
    "totalDoses" to totalDoses,
    "startAtMillis" to startAtMillis,
    "reasonId" to reasonId,
    "reasonNombre" to reasonNombre,
    "status" to status,
    "createdAt" to createdAtMillis.asTimestamp(),
)

private fun BackupDose.toFirestore() = mapOf(
    "doseNumber" to doseNumber,
    "scheduledAtMillis" to scheduledAtMillis,
    "status" to status,
    "takenAtMillis" to takenAtMillis,
    "historyEntryId" to historyEntryId,
)

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
