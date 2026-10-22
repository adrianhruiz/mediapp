package com.medicapp.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.medicapp.domain.DoseScheduleCalculator
import com.medicapp.domain.PendingDoseCounter
import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule
import com.medicapp.model.ScheduleStatus
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.tasks.await
import java.util.concurrent.ConcurrentHashMap

class FirestoreScheduleRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
) : ScheduleRepository {

    private fun schedulesCollection() = firestore.collection("users")
        .document(authRepository.currentUserId ?: error("No hay sesión iniciada"))
        .collection("schedules")

    private fun dosesCollection(scheduleId: String) =
        schedulesCollection().document(scheduleId).collection("doses")

    /** Usuarios cuyas pautas activas ya llevan [END_AT_FIELD] en este arranque. */
    private val scheduleEndsChecked: MutableSet<String> = ConcurrentHashMap.newKeySet()

    /**
     * Dos filtros en Firestore: las pautas cuya última toma no es anterior a [fromMillis], y
     * dentro de cada una solo las tomas del tramo. El tramo va sobre un único campo, así que no
     * hace falta índice compuesto.
     *
     * Las pautas que aún no empiezan se descartan en el móvil para no pedir un segundo rango
     * (eso sí exigiría índice); son documentos de pauta, no tomas, y su consulta de tomas ni se
     * lanza.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeDosesBetween(fromMillis: Long, toMillis: Long): Flow<List<ScheduledDose>> =
        observeSchedulesEndingFrom(fromMillis)
            .onStart { backfillScheduleEnds() }
            .map { schedules -> schedules.filter { it.startAtMillis < toMillis } }
            // El contador de pendientes cambia la pauta en cada toma; sin esto se volverían a
            // abrir todos los listeners de tomas cada vez que se marca una.
            .distinctUntilChanged()
            .flatMapLatest { schedules ->
                if (schedules.isEmpty()) {
                    flowOf(emptyList())
                } else {
                    combine(schedules.map { observeDosesIn(it, fromMillis, toMillis) }) { parts ->
                        parts.flatMap { it }.sortedBy { it.dose.scheduledAtMillis }
                    }
                }
            }

    private fun observeSchedulesEndingFrom(fromMillis: Long): Flow<List<MedicationSchedule>> = callbackFlow {
        val registration = schedulesCollection()
            .whereGreaterThanOrEqualTo(END_AT_FIELD, fromMillis)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ScheduleRepository", "Schedules in range listener failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toMedicationSchedule() })
            }
        awaitClose { registration.remove() }
    }

    private fun observeDosesIn(
        schedule: MedicationSchedule,
        fromMillis: Long,
        toMillis: Long,
    ): Flow<List<ScheduledDose>> = callbackFlow {
        val registration = dosesCollection(schedule.id)
            .whereGreaterThanOrEqualTo("scheduledAtMillis", fromMillis)
            .whereLessThan("scheduledAtMillis", toMillis)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ScheduleRepository", "Doses in range listener failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents.orEmpty()
                        .mapNotNull { it.toMedicationDose(schedule.id) }
                        .map { ScheduledDose(schedule, it) },
                )
            }
        awaitClose { registration.remove() }
    }

    /**
     * Las pautas creadas antes de [END_AT_FIELD] no lo tienen, y sin él la consulta por fechas no
     * las encuentra. Una vez por arranque se repasan las activas (para hoy y lo que viene) y las
     * [RECENT_SCHEDULES_TO_BACKFILL] más recientes (para las últimas semanas del historial), las
     * dos consultas acotadas, y se les apunta el final. Las cerradas más antiguas no se tocan:
     * solo faltarían en semanas viejas, anteriores al cambio.
     */
    private suspend fun backfillScheduleEnds() {
        val userId = authRepository.currentUserId ?: return
        if (userId in scheduleEndsChecked) return
        runCatching {
            val active = schedulesCollection()
                .whereEqualTo("status", ScheduleStatus.ACTIVE.name)
                .get()
                .await()
                .documents
            val recent = schedulesCollection()
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .limit(RECENT_SCHEDULES_TO_BACKFILL)
                .get()
                .await()
                .documents
            val missing = (active + recent)
                .distinctBy { it.id }
                .filter { it.getLong(END_AT_FIELD) == null }
            if (missing.isNotEmpty()) {
                val batch = firestore.batch()
                missing.forEach { doc ->
                    val schedule = doc.toMedicationSchedule() ?: return@forEach
                    batch.update(doc.reference, END_AT_FIELD, schedule.endAtMillis())
                }
                batch.commit().await()
            }
            scheduleEndsChecked += userId
        }.onFailure { Log.w("ScheduleRepository", "Could not backfill schedule ends", it) }
    }

    /**
     * Pide una fila de más que el límite pedido: si llega, es que hay página siguiente. Comparar
     * `size >= limit` en su lugar deja un indicador de carga infinito cuando el total es múltiplo
     * exacto del tamaño de página.
     */
    override fun getSchedulesPage(limit: Long): Flow<PagedResult<MedicationSchedule>> = callbackFlow {
        val registration = schedulesCollection()
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .limit(limit + 1)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ScheduleRepository", "Schedules page listener failed", error)
                    trySend(PagedResult(emptyList(), hasMore = false, isFromCache = false))
                    return@addSnapshotListener
                }
                val loaded = snapshot?.documents.orEmpty().mapNotNull { it.toMedicationSchedule() }
                trySend(
                    PagedResult(
                        items = loaded.take(limit.toInt()),
                        hasMore = loaded.size > limit,
                        isFromCache = snapshot?.metadata?.isFromCache ?: false,
                    ),
                )
            }
        awaitClose { registration.remove() }
    }

    override fun getActiveScheduleNregistros(): Flow<Set<String>> = callbackFlow {
        val registration = schedulesCollection()
            .whereEqualTo("status", ScheduleStatus.ACTIVE.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ScheduleRepository", "Active schedules listener failed", error)
                    trySend(emptySet())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.getString("nregistro") }.toSet())
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

    override fun observeSchedule(scheduleId: String): Flow<MedicationSchedule?> = callbackFlow {
        val registration = schedulesCollection().document(scheduleId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ScheduleRepository", "Schedule listener failed", error)
                    trySend(null)
                    return@addSnapshotListener
                }
                trySend(snapshot?.toMedicationSchedule())
            }
        awaitClose { registration.remove() }
    }

    override suspend fun getSchedule(scheduleId: String): MedicationSchedule? =
        schedulesCollection().document(scheduleId).get().await().toMedicationSchedule()

    override suspend fun hasActiveScheduleFor(nregistro: String): Boolean =
        schedulesCollection()
            .whereEqualTo("nregistro", nregistro)
            .whereEqualTo("status", ScheduleStatus.ACTIVE.name)
            .limit(1)
            .get()
            .await()
            .isEmpty
            .not()

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
                PendingDoseCounter.FIELD to totalDoses,
                END_AT_FIELD to DoseScheduleCalculator.endAtMillis(startAtMillis, intervalHours, totalDoses),
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

    /**
     * La dosis y el contador de la pauta se escriben en el mismo lote, así que marcar una dosis
     * no necesita consultar las demás para saber si la pauta ha terminado.
     *
     * El contador se resta con `increment` en vez de escribir el número calculado: si dos dosis
     * se marcan a la vez —desde la notificación y desde la pantalla— ninguna pisa a la otra.
     */
    override suspend fun markDoseTaken(scheduleId: String, doseId: String, historyEntryId: String) {
        val scheduleRef = schedulesCollection().document(scheduleId)
        val pendingBefore = scheduleRef.get().await().getLong(PendingDoseCounter.FIELD)

        val batch = firestore.batch()
        batch.update(dosesCollection(scheduleId).document(doseId), takenDoseData(historyEntryId))

        if (pendingBefore == null) {
            // Pauta anterior al contador: se cuenta a mano esta vez y se deja puesto.
            batch.commit().await()
            backfillPendingDoses(scheduleId, scheduleRef)
            return
        }

        val pendingAfter = PendingDoseCounter.pendingAfterTaking(pendingBefore)
        batch.update(scheduleRef, scheduleUpdate(FieldValue.increment(-1), pendingAfter))
        batch.commit().await()
    }

    private suspend fun backfillPendingDoses(scheduleId: String, scheduleRef: DocumentReference) {
        val pendingAfter = dosesCollection(scheduleId)
            .whereEqualTo("status", DoseStatus.PENDING.name)
            .get()
            .await()
            .size()
            .toLong()
        scheduleRef.update(scheduleUpdate(pendingAfter, pendingAfter)).await()
    }

    /** Deja la cuenta pendiente y, si era la última dosis, cierra la pauta de paso. */
    private fun scheduleUpdate(pendingValue: Any, pendingAfter: Long): Map<String, Any> = buildMap {
        put(PendingDoseCounter.FIELD, pendingValue)
        if (PendingDoseCounter.closesSchedule(pendingAfter)) put("status", ScheduleStatus.CLOSED.name)
    }

    private fun takenDoseData(historyEntryId: String) = mapOf(
        "status" to DoseStatus.TAKEN.name,
        "takenAtMillis" to System.currentTimeMillis(),
        "historyEntryId" to historyEntryId,
    )

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
        batch.update(
            schedulesCollection().document(scheduleId),
            mapOf("status" to ScheduleStatus.CLOSED.name, PendingDoseCounter.FIELD to 0),
        )
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

    companion object {
        /** Hora de la última toma de la pauta, para filtrar pautas por fechas en la consulta. */
        const val END_AT_FIELD = "endAtMillis"

        private const val RECENT_SCHEDULES_TO_BACKFILL = 20L
    }
}

private fun MedicationSchedule.endAtMillis() =
    DoseScheduleCalculator.endAtMillis(startAtMillis, intervalHours, totalDoses)

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
