package com.medicapp.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.medicapp.domain.DoseScheduleCalculator
import com.medicapp.domain.PendingDoseCounter
import com.medicapp.domain.ScheduleEditPlanner
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

    /**
     * Pautas activas (las cerradas pasan todas sus tomas pendientes a saltadas) y, de cada una,
     * sus tomas pendientes. Filtrar además por hora en la consulta exigiría un índice compuesto
     * (igualdad en el estado y rango en la hora); las pendientes de una pauta son pocas y las
     * futuras se descartan aquí.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    override fun observeMissedDoses(beforeMillis: Long): Flow<List<ScheduledDose>> = callbackFlow {
        val registration = schedulesCollection()
            .whereEqualTo("status", ScheduleStatus.ACTIVE.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ScheduleRepository", "Active schedules listener failed", error)
                    trySend(emptyList<MedicationSchedule>())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toMedicationSchedule() })
            }
        awaitClose { registration.remove() }
    }
        .map { schedules -> schedules.filter { it.startAtMillis < beforeMillis } }
        .distinctUntilChanged()
        .flatMapLatest { schedules ->
            if (schedules.isEmpty()) {
                flowOf(emptyList())
            } else {
                combine(schedules.map { observePendingBefore(it, beforeMillis) }) { parts ->
                    parts.flatMap { it }.sortedBy { it.dose.scheduledAtMillis }
                }
            }
        }

    private fun observePendingBefore(
        schedule: MedicationSchedule,
        beforeMillis: Long,
    ): Flow<List<ScheduledDose>> = callbackFlow {
        val registration = dosesCollection(schedule.id)
            .whereEqualTo("status", DoseStatus.PENDING.name)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ScheduleRepository", "Pending doses listener failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents.orEmpty()
                        .mapNotNull { it.toMedicationDose(schedule.id) }
                        .filter { it.scheduledAtMillis < beforeMillis }
                        .map { ScheduledDose(schedule, it) },
                )
            }
        awaitClose { registration.remove() }
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
        openEnded: Boolean,
        nowMillis: Long,
    ): ScheduleWithDoses {
        val scheduleRef = schedulesCollection().document()
        val storedTotal = if (openEnded) 0 else totalDoses
        val doseTimes = if (openEnded) {
            DoseScheduleCalculator.openEndedWindow(startAtMillis, intervalHours, nowMillis)
        } else {
            DoseScheduleCalculator.computeDoseTimes(startAtMillis, intervalHours, totalDoses)
        }
        val batch = firestore.batch()
        batch.set(
            scheduleRef,
            mapOf(
                "nregistro" to nregistro,
                "nombreMedicamento" to nombreMedicamento,
                "fotoUrl" to fotoUrl,
                "intervalHours" to intervalHours,
                "totalDoses" to storedTotal,
                "startAtMillis" to startAtMillis,
                "reasonId" to reasonId,
                "reasonNombre" to reasonNombre,
                "status" to ScheduleStatus.ACTIVE.name,
                OPEN_ENDED_FIELD to openEnded,
                PendingDoseCounter.FIELD to doseTimes.size,
                LAST_DOSE_NUMBER_FIELD to doseTimes.size,
                // En una pauta sin fin es la última toma creada hasta ahora; avanza al reponer.
                END_AT_FIELD to (doseTimes.lastOrNull() ?: startAtMillis),
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        )
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
            totalDoses = storedTotal,
            startAtMillis = startAtMillis,
            reasonId = reasonId,
            reasonNombre = reasonNombre,
            status = ScheduleStatus.ACTIVE,
            openEnded = openEnded,
        )
        return ScheduleWithDoses(schedule, doses)
    }

    /**
     * Va en una transacción y no en un lote: si una toma se marca desde la notificación mientras
     * se edita, Firestore repite la edición con la toma ya hecha en vez de borrarla como
     * pendiente. Por eso editar necesita conexión, a diferencia de crear.
     *
     * Las tomas de una pauta son pocas y aquí hacen falta todas: las hechas para numerar por
     * hora y las pendientes para sustituirlas. Como la transacción no puede consultar, la lista se
     * pide antes, junto con [EDIT_VERSION_FIELD]; si otra edición (un doble toque en Guardar) la
     * cambia entre medias, la lista ya no vale y se vuelve a pedir en lugar de seguir con ella.
     */
    override suspend fun updateSchedule(
        scheduleId: String,
        intervalHours: Int,
        totalDoses: Int,
        nextDoseAtMillis: Long,
        reasonId: String,
        reasonNombre: String,
        openEnded: Boolean,
        nowMillis: Long,
    ): ScheduleEditResult? {
        val scheduleRef = schedulesCollection().document(scheduleId)
        repeat(MAX_EDIT_ATTEMPTS) {
            val editVersion = scheduleRef.get().await().editVersion()
            val doseRefs = dosesCollection(scheduleId).get().await().documents.map { it.reference }
            val outcome = runEditTransaction(
                scheduleRef, doseRefs, editVersion, scheduleId,
                intervalHours, totalDoses, nextDoseAtMillis, reasonId, reasonNombre, openEnded, nowMillis,
            )
            if (outcome !== StaleDoseList) return outcome as ScheduleEditResult?
        }
        error("La pauta $scheduleId cambió durante la edición demasiadas veces")
    }

    /** Devuelve [StaleDoseList] si otra edición se coló después de pedir las tomas. */
    private suspend fun runEditTransaction(
        scheduleRef: DocumentReference,
        doseRefs: List<DocumentReference>,
        editVersion: Long,
        scheduleId: String,
        intervalHours: Int,
        totalDoses: Int,
        nextDoseAtMillis: Long,
        reasonId: String,
        reasonNombre: String,
        openEnded: Boolean,
        nowMillis: Long,
    ): Any? =
        firestore.runTransaction { transaction ->
            val scheduleSnapshot = transaction.get(scheduleRef)
            if (scheduleSnapshot.editVersion() != editVersion) return@runTransaction StaleDoseList
            val schedule = scheduleSnapshot.toMedicationSchedule()
            if (schedule?.status != ScheduleStatus.ACTIVE) return@runTransaction null
            val doses = doseRefs.mapNotNull { transaction.get(it).toMedicationDose(scheduleId) }
            val plan = if (openEnded) {
                ScheduleEditPlanner.planOpenEnded(doses, intervalHours, nextDoseAtMillis, nowMillis)
            } else {
                ScheduleEditPlanner.plan(doses, intervalHours, totalDoses, nextDoseAtMillis)
            } ?: return@runTransaction null
            val storedTotal = if (openEnded) 0 else totalDoses

            val removed = doses.filter { it.status == DoseStatus.PENDING }
            removed.forEach { transaction.delete(dosesCollection(scheduleId).document(it.id)) }
            plan.renumberedKeptDoses.forEach { (doseId, doseNumber) ->
                transaction.update(dosesCollection(scheduleId).document(doseId), "doseNumber", doseNumber)
            }
            val newDoses = plan.newDoses.map { planned ->
                val doseRef = dosesCollection(scheduleId).document()
                transaction.set(
                    doseRef,
                    mapOf(
                        "doseNumber" to planned.doseNumber,
                        "scheduledAtMillis" to planned.scheduledAtMillis,
                        "status" to DoseStatus.PENDING.name,
                    ),
                )
                MedicationDose(
                    id = doseRef.id,
                    scheduleId = scheduleId,
                    doseNumber = planned.doseNumber,
                    scheduledAtMillis = planned.scheduledAtMillis,
                    status = DoseStatus.PENDING,
                    takenAtMillis = null,
                    historyEntryId = null,
                )
            }
            transaction.update(
                scheduleRef,
                mapOf(
                    "intervalHours" to intervalHours,
                    "totalDoses" to storedTotal,
                    "startAtMillis" to plan.startAtMillis,
                    "reasonId" to reasonId,
                    "reasonNombre" to reasonNombre,
                    OPEN_ENDED_FIELD to openEnded,
                    LAST_DOSE_NUMBER_FIELD to doses.size - removed.size + newDoses.size,
                    PendingDoseCounter.FIELD to newDoses.size,
                    END_AT_FIELD to plan.endAtMillis,
                    EDIT_VERSION_FIELD to editVersion + 1,
                ),
            )
            ScheduleEditResult(
                schedule = schedule.copy(
                    intervalHours = intervalHours,
                    totalDoses = storedTotal,
                    openEnded = openEnded,
                    startAtMillis = plan.startAtMillis,
                    reasonId = reasonId,
                    reasonNombre = reasonNombre,
                ),
                removedDoseIds = removed.map { it.id },
                newDoses = newDoses,
            )
        }.await()

    /**
     * La dosis y el contador de la pauta se escriben en el mismo lote, así que marcar una dosis
     * no necesita consultar las demás para saber si la pauta ha terminado.
     *
     * El contador se resta con `increment` en vez de escribir el número calculado: si dos dosis
     * se marcan a la vez —desde la notificación y desde la pantalla— ninguna pisa a la otra.
     *
     * Una pauta sin fin nunca se cierra aquí: quedarse sin pendientes solo significa que toca
     * reponer (ver [replenishOpenEndedDoses]).
     */
    override suspend fun markDoseTaken(scheduleId: String, doseId: String, historyEntryId: String) {
        val scheduleRef = schedulesCollection().document(scheduleId)
        val scheduleSnapshot = scheduleRef.get().await()
        val pendingBefore = scheduleSnapshot.getLong(PendingDoseCounter.FIELD)
        val openEnded = scheduleSnapshot.getBoolean(OPEN_ENDED_FIELD) == true

        val batch = firestore.batch()
        batch.update(dosesCollection(scheduleId).document(doseId), takenDoseData(historyEntryId))

        if (pendingBefore == null) {
            // Pauta anterior al contador: se cuenta a mano esta vez y se deja puesto.
            batch.commit().await()
            backfillPendingDoses(scheduleId, scheduleRef, openEnded)
            return
        }

        val pendingAfter = PendingDoseCounter.pendingAfterTaking(pendingBefore)
        batch.update(scheduleRef, scheduleUpdate(FieldValue.increment(-1), pendingAfter, openEnded))
        batch.commit().await()
    }

    private suspend fun backfillPendingDoses(scheduleId: String, scheduleRef: DocumentReference, openEnded: Boolean) {
        val pendingAfter = dosesCollection(scheduleId)
            .whereEqualTo("status", DoseStatus.PENDING.name)
            .get()
            .await()
            .size()
            .toLong()
        scheduleRef.update(scheduleUpdate(pendingAfter, pendingAfter, openEnded)).await()
    }

    /** Deja la cuenta pendiente y, si era la última dosis de una pauta con total, la cierra de paso. */
    private fun scheduleUpdate(pendingValue: Any, pendingAfter: Long, openEnded: Boolean): Map<String, Any> = buildMap {
        put(PendingDoseCounter.FIELD, pendingValue)
        if (!openEnded && PendingDoseCounter.closesSchedule(pendingAfter)) put("status", ScheduleStatus.CLOSED.name)
    }

    /**
     * Va en una transacción sobre el documento de la pauta: reponer se lanza a la vez desde
     * sitios distintos (marcar una toma, abrir la app, reiniciar el móvil) y, con un lote, dos
     * reposiciones a la vez crearían las mismas tomas dos veces. La transacción solo lee la
     * pauta: [END_AT_FIELD] y [LAST_DOSE_NUMBER_FIELD] dicen dónde se quedó, sin consultar sus
     * tomas. Las tomas nuevas no cambian el modelo de la pauta, así que no reabren los
     * listeners de [observeDosesBetween].
     *
     * Sube [EDIT_VERSION_FIELD] porque cambia la lista de tomas: una edición o un cierre que la
     * pidieron antes la descartan y la vuelven a pedir, en vez de dejar viva la toma repuesta con
     * la frecuencia vieja. Si la pauta ya se cerró, no crea nada.
     */
    override suspend fun replenishOpenEndedDoses(scheduleId: String, nowMillis: Long): List<MedicationDose> {
        val added = mutableListOf<MedicationDose>()
        // Un hueco largo puede pasar del límite de escrituras de una transacción: se repone por
        // tramos, y cada uno vuelve a leer dónde se quedó el anterior.
        while (true) {
            val chunk = replenishChunk(scheduleId, nowMillis)
            added += chunk
            if (chunk.size < DoseScheduleCalculator.MAX_DOSES_PER_REFILL) return added
        }
    }

    /** Un tramo de la reposición, de [DoseScheduleCalculator.MAX_DOSES_PER_REFILL] tomas como mucho. */
    private suspend fun replenishChunk(scheduleId: String, nowMillis: Long): List<MedicationDose> {
        val scheduleRef = schedulesCollection().document(scheduleId)
        return firestore.runTransaction { transaction ->
            val snapshot = transaction.get(scheduleRef)
            val schedule = snapshot.toMedicationSchedule()
            if (schedule == null || !schedule.openEnded || schedule.status != ScheduleStatus.ACTIVE) {
                return@runTransaction emptyList<MedicationDose>()
            }
            val lastAt = snapshot.getLong(END_AT_FIELD) ?: return@runTransaction emptyList<MedicationDose>()
            val lastNumber = (snapshot.getLong(LAST_DOSE_NUMBER_FIELD) ?: return@runTransaction emptyList<MedicationDose>()).toInt()
            val times = DoseScheduleCalculator.openEndedReplenishChunk(lastAt, schedule.intervalHours, nowMillis)
            if (times.isEmpty()) return@runTransaction emptyList<MedicationDose>()

            val added = times.mapIndexed { index, scheduledAtMillis ->
                val doseRef = dosesCollection(scheduleId).document()
                val doseNumber = lastNumber + index + 1
                transaction.set(
                    doseRef,
                    mapOf(
                        "doseNumber" to doseNumber,
                        "scheduledAtMillis" to scheduledAtMillis,
                        "status" to DoseStatus.PENDING.name,
                    ),
                )
                MedicationDose(
                    id = doseRef.id,
                    scheduleId = scheduleId,
                    doseNumber = doseNumber,
                    scheduledAtMillis = scheduledAtMillis,
                    status = DoseStatus.PENDING,
                    takenAtMillis = null,
                    historyEntryId = null,
                )
            }
            transaction.update(
                scheduleRef,
                mapOf(
                    END_AT_FIELD to times.last(),
                    LAST_DOSE_NUMBER_FIELD to lastNumber + times.size,
                    PendingDoseCounter.FIELD to FieldValue.increment(times.size.toLong()),
                    EDIT_VERSION_FIELD to FieldValue.increment(1),
                ),
            )
            added
        }.await()
    }

    /** Solo las pautas activas y sin fin: dos igualdades, que Firestore resuelve sin índice compuesto. */
    override suspend fun replenishOpenEndedSchedules(nowMillis: Long) {
        schedulesCollection()
            .whereEqualTo("status", ScheduleStatus.ACTIVE.name)
            .whereEqualTo(OPEN_ENDED_FIELD, true)
            .get()
            .await()
            .documents
            .forEach { doc ->
                runCatching { replenishOpenEndedDoses(doc.id, nowMillis) }
                    .onFailure { Log.w("ScheduleRepository", "Could not replenish open-ended schedule", it) }
            }
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

    /**
     * Mismo patrón que [updateSchedule]: las pendientes se piden antes junto con
     * [EDIT_VERSION_FIELD] y se cierran en una transacción que lo vuelve a leer. Si una
     * reposición (o una edición) se coló entre medias, la lista ya no vale y se pide de nuevo;
     * con un lote, la toma repuesta quedaría pendiente bajo una pauta cerrada, con su alarma.
     * Por eso cerrar necesita conexión, como editar.
     *
     * Dentro de la transacción cada toma se vuelve a leer y solo se omite si sigue pendiente:
     * una que se marcó desde la notificación mientras tanto se queda como tomada.
     */
    override suspend fun closeSchedule(scheduleId: String): List<String> {
        val scheduleRef = schedulesCollection().document(scheduleId)
        repeat(MAX_EDIT_ATTEMPTS) {
            val editVersion = scheduleRef.get().await().editVersion()
            val pendingRefs = dosesCollection(scheduleId)
                .whereEqualTo("status", DoseStatus.PENDING.name)
                .get()
                .await()
                .documents
                .map { it.reference }
            val skipped = firestore.runTransaction { transaction ->
                if (transaction.get(scheduleRef).editVersion() != editVersion) {
                    return@runTransaction null
                }
                val stillPending = pendingRefs.filter {
                    transaction.get(it).getString("status") == DoseStatus.PENDING.name
                }
                stillPending.forEach { transaction.update(it, "status", DoseStatus.SKIPPED.name) }
                transaction.update(
                    scheduleRef,
                    mapOf(
                        "status" to ScheduleStatus.CLOSED.name,
                        PendingDoseCounter.FIELD to 0,
                        EDIT_VERSION_FIELD to editVersion + 1,
                    ),
                )
                stillPending.map { it.id }
            }.await()
            if (skipped != null) return skipped
        }
        error("La pauta $scheduleId cambió durante el cierre demasiadas veces")
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

        /** Pauta sin fin: dura hasta que se cierra y repone sus tomas por delante. */
        const val OPEN_ENDED_FIELD = "openEnded"

        /** Número de la última toma creada, para seguir numerando las que se reponen. */
        const val LAST_DOSE_NUMBER_FIELD = "lastDoseNumber"

        /** Cuenta de ediciones de la pauta, para detectar dos ediciones a la vez. */
        private const val EDIT_VERSION_FIELD = "editVersion"

        private const val MAX_EDIT_ATTEMPTS = 3

        /** Marca de que la lista de tomas pedida antes de la transacción quedó obsoleta. */
        private object StaleDoseList

        private fun DocumentSnapshot.editVersion(): Long = getLong(EDIT_VERSION_FIELD) ?: 0L

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
        openEnded = getBoolean(FirestoreScheduleRepository.OPEN_ENDED_FIELD) ?: false,
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
