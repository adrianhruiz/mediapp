package com.medicapp.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.medicapp.domain.DoseScheduleCalculator
import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule
import com.medicapp.model.ScheduleStatus
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreScheduleRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
) : ScheduleRepository {

    private fun schedulesCollection() = firestore.collection("users")
        .document(authRepository.currentUserId ?: error("No hay sesión iniciada"))
        .collection("schedules")

    private fun dosesCollection(scheduleId: String) =
        schedulesCollection().document(scheduleId).collection("doses")

    override fun getSchedules(): Flow<List<MedicationSchedule>> = callbackFlow {
        val registration = schedulesCollection()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ScheduleRepository", "Schedules listener failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toMedicationSchedule() })
            }
        awaitClose { registration.remove() }
    }

    override fun getSchedulesPage(limit: Long): Flow<PagedResult<MedicationSchedule>> = callbackFlow {
        val registration = schedulesCollection()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ScheduleRepository", "Schedules page listener failed", error)
                    trySend(PagedResult(emptyList(), hasMore = false, isFromCache = false))
                    return@addSnapshotListener
                }
                val items = snapshot?.documents.orEmpty().mapNotNull { it.toMedicationSchedule() }
                trySend(
                    PagedResult(
                        items = items,
                        hasMore = items.size.toLong() >= limit,
                        isFromCache = snapshot?.metadata?.isFromCache ?: false,
                    ),
                )
            }
        awaitClose { registration.remove() }
    }

    override fun getDoses(scheduleId: String): Flow<List<MedicationDose>> = callbackFlow {
        val registration = dosesCollection(scheduleId)
            .orderBy("doseNumber")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ScheduleRepository", "Doses listener failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toMedicationDose(scheduleId) })
            }
        awaitClose { registration.remove() }
    }

    override suspend fun getSchedule(scheduleId: String): MedicationSchedule? =
        schedulesCollection().document(scheduleId).get().await().toMedicationSchedule()

    override suspend fun getDose(scheduleId: String, doseId: String): MedicationDose? =
        dosesCollection(scheduleId).document(doseId).get().await().toMedicationDose(scheduleId)

    override suspend fun createSchedule(
        nregistro: String,
        nombreMedicamento: String,
        fotoUrl: String?,
        intervalHours: Int,
        totalDoses: Int,
        startAtMillis: Long,
        reasonId: String,
        reasonNombre: String,
    ): ScheduleWithDoses {
        val scheduleRef = schedulesCollection().document()
        val batch = firestore.batch()
        batch.set(
            scheduleRef,
            mapOf(
                "nregistro" to nregistro,
                "nombreMedicamento" to nombreMedicamento,
                "fotoUrl" to fotoUrl,
                "intervalHours" to intervalHours,
                "totalDoses" to totalDoses,
                "startAtMillis" to startAtMillis,
                "reasonId" to reasonId,
                "reasonNombre" to reasonNombre,
                "status" to ScheduleStatus.ACTIVE.name,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
        val doseTimes = DoseScheduleCalculator.computeDoseTimes(startAtMillis, intervalHours, totalDoses)
        val doses = doseTimes.mapIndexed { index, scheduledAtMillis ->
            val doseNumber = index + 1
            val doseRef = dosesCollection(scheduleRef.id).document()
            batch.set(
                doseRef,
                mapOf(
                    "doseNumber" to doseNumber,
                    "scheduledAtMillis" to scheduledAtMillis,
                    "status" to DoseStatus.PENDING.name,
                ),
            )
            MedicationDose(
                id = doseRef.id,
                scheduleId = scheduleRef.id,
                doseNumber = doseNumber,
                scheduledAtMillis = scheduledAtMillis,
                status = DoseStatus.PENDING,
                takenAtMillis = null,
                historyEntryId = null,
            )
        }
        batch.commit().await()
        val schedule = MedicationSchedule(
            id = scheduleRef.id,
            nregistro = nregistro,
            nombreMedicamento = nombreMedicamento,
            fotoUrl = fotoUrl,
            intervalHours = intervalHours,
            totalDoses = totalDoses,
            startAtMillis = startAtMillis,
            reasonId = reasonId,
            reasonNombre = reasonNombre,
            status = ScheduleStatus.ACTIVE,
        )
        return ScheduleWithDoses(schedule, doses)
    }

    override suspend fun markDoseTaken(scheduleId: String, doseId: String, historyEntryId: String) {
        dosesCollection(scheduleId).document(doseId).update(
            mapOf(
                "status" to DoseStatus.TAKEN.name,
                "takenAtMillis" to System.currentTimeMillis(),
                "historyEntryId" to historyEntryId,
            ),
        ).await()

        val remainingPending = dosesCollection(scheduleId)
            .whereEqualTo("status", DoseStatus.PENDING.name)
            .limit(1)
            .get()
            .await()
        if (remainingPending.isEmpty) {
            schedulesCollection().document(scheduleId).update("status", ScheduleStatus.CLOSED.name).await()
        }
    }

    override suspend fun getPendingDoses(scheduleId: String): List<MedicationDose> =
        dosesCollection(scheduleId)
            .whereEqualTo("status", DoseStatus.PENDING.name)
            .get()
            .await()
            .documents
            .mapNotNull { it.toMedicationDose(scheduleId) }

    override suspend fun closeSchedule(scheduleId: String) {
        val pendingDoses = dosesCollection(scheduleId)
            .whereEqualTo("status", DoseStatus.PENDING.name)
            .get()
            .await()
        val batch = firestore.batch()
        pendingDoses.documents.forEach { doc ->
            batch.update(doc.reference, "status", DoseStatus.SKIPPED.name)
        }
        batch.update(schedulesCollection().document(scheduleId), "status", ScheduleStatus.CLOSED.name)
        batch.commit().await()
    }

    override suspend fun getActiveSchedulesWithPendingDoses(): List<ScheduleWithDoses> {
        val activeSchedules = schedulesCollection()
            .whereEqualTo("status", ScheduleStatus.ACTIVE.name)
            .get()
            .await()
            .documents
            .mapNotNull { it.toMedicationSchedule() }
        return activeSchedules.map { schedule ->
            ScheduleWithDoses(schedule, getPendingDoses(schedule.id))
        }
    }
}

private fun DocumentSnapshot.toMedicationSchedule(): MedicationSchedule? {
    if (!exists()) return null
    val statusRaw = getString("status") ?: return null
    val status = runCatching { ScheduleStatus.valueOf(statusRaw) }.getOrNull() ?: return null
    return MedicationSchedule(
        id = id,
        nregistro = getString("nregistro") ?: return null,
        nombreMedicamento = getString("nombreMedicamento") ?: return null,
        fotoUrl = getString("fotoUrl"),
        intervalHours = (getLong("intervalHours") ?: return null).toInt(),
        totalDoses = (getLong("totalDoses") ?: return null).toInt(),
        startAtMillis = getLong("startAtMillis") ?: return null,
        reasonId = getString("reasonId") ?: return null,
        reasonNombre = getString("reasonNombre") ?: return null,
        status = status,
    )
}

private fun DocumentSnapshot.toMedicationDose(scheduleId: String): MedicationDose? {
    if (!exists()) return null
    val statusRaw = getString("status") ?: return null
    val status = runCatching { DoseStatus.valueOf(statusRaw) }.getOrNull() ?: return null
    return MedicationDose(
        id = id,
        scheduleId = scheduleId,
        doseNumber = (getLong("doseNumber") ?: return null).toInt(),
        scheduledAtMillis = getLong("scheduledAtMillis") ?: return null,
        status = status,
        takenAtMillis = getLong("takenAtMillis"),
        historyEntryId = getString("historyEntryId"),
    )
}
