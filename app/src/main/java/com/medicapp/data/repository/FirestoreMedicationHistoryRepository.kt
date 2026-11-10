package com.medicapp.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
import com.medicapp.model.SavedMedication
import com.medicapp.model.TakenEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.concurrent.atomic.AtomicBoolean

class FirestoreMedicationHistoryRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
) : MedicationHistoryRepository {

    private fun userDocument() = firestore.collection("users")
        .document(authRepository.currentUserId ?: error("No hay sesión iniciada"))

    private fun collection() = userDocument().collection("takenMedications")

    private fun summaryDocument() = userDocument()
        .collection(HistorySummary.COLLECTION)
        .document(HistorySummary.DOCUMENT)

    /** Evita lanzar dos reconstrucciones a la vez si llegan dos respuestas seguidas. */
    private val rebuildingSummary = AtomicBoolean(false)

    /**
     * Pide una fila de más que el límite pedido: si llega, es que hay página siguiente. Comparar
     * `size >= limit` en su lugar deja un indicador de carga infinito cuando el total es múltiplo
     * exacto del tamaño de página.
     *
     * Escucha con [MetadataChanges.INCLUDE] porque `isFromCache` **es** un metadato. Por defecto
     * el listener solo avisa cuando cambian los datos, así que la confirmación del servidor —que
     * es justo cuando `isFromCache` pasa a false— no llegaba nunca si los datos no habían
     * cambiado, y el aviso de "sin conexión" se quedaba encendido para siempre.
     */
    override fun getPage(limit: Long): Flow<PagedResult<TakenEntry>> = callbackFlow {
        val registration = collection()
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit + 1)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) {
                    Log.w("MedicationHistoryRepository", "Taken medications listener failed", error)
                    trySend(PagedResult(emptyList(), hasMore = false, isFromCache = false))
                    return@addSnapshotListener
                }
                val loaded = snapshot?.documents.orEmpty().mapNotNull { it.toTakenEntry() }
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

    override fun getByMedication(nregistro: String): Flow<List<TakenEntry>> = callbackFlow {
        val registration = collection()
            .whereEqualTo("nregistro", nregistro)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MedicationHistoryRepository", "Medication entries listener failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(snapshot?.documents.orEmpty().mapNotNull { it.toTakenEntry() })
            }
        awaitClose { registration.remove() }
    }

    /**
     * Sale del resumen, no del historial: una lectura en vez de una por entrada guardada.
     *
     * Si el resumen todavía no existe —cuenta anterior a este cambio, o restauración de una
     * copia— o es de antes de guardar nombres y fotos, se rehace una sola vez leyendo el historial
     * completo, que es justo lo que antes se hacía en cada consulta.
     */
    override fun getMedicationNregistros(): Flow<List<SavedMedication>> = callbackFlow {
        val registration = summaryDocument().addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("MedicationHistoryRepository", "Medication summary listener failed", error)
                trySend(emptyList())
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val counts = snapshot?.get(HistorySummary.NREGISTROS_FIELD) as? Map<String, Any?>
            @Suppress("UNCHECKED_CAST")
            val medicamentos = snapshot?.get(HistorySummary.MEDICAMENTOS_FIELD) as? Map<String, Any?>
            trySend(HistorySummary.savedMedications(counts, medicamentos))

            val rebuild = HistorySummary.shouldRebuild(
                exists = snapshot?.exists() == true,
                fromServer = snapshot?.metadata?.isFromCache == false,
                counts = counts,
                medicamentos = medicamentos,
            )
            if (rebuild) launch { rebuildSummary() }
        }
        awaitClose { registration.remove() }
    }

    private suspend fun rebuildSummary() {
        if (!rebuildingSummary.compareAndSet(false, true)) return
        try {
            val entries = collection().get().await().documents.mapNotNull { doc ->
                val nregistro = doc.getString("nregistro") ?: return@mapNotNull null
                // Sin nombre se usa el número de registro: así todos los contados tienen uno y el
                // resumen no vuelve a pedir que se rehaga.
                SavedMedication(nregistro, doc.getString("nombreMedicamento") ?: nregistro, doc.getString("fotoUrl"))
            }
            summaryDocument().set(HistorySummary.rebuild(entries)).await()
        } catch (e: Exception) {
            // Si falla, el resumen sigue sin rehacer y volverá a intentarse; mientras tanto la
            // búsqueda solo pierde los distintivos y los guardados del historial.
            Log.w("MedicationHistoryRepository", "No se pudo rehacer el resumen del historial", e)
        } finally {
            rebuildingSummary.set(false)
        }
    }

    override suspend fun getById(id: String): TakenEntry? =
        collection().document(id).get().await().toTakenEntry()

    override suspend fun add(
        nregistro: String,
        nombreMedicamento: String,
        reasonId: String,
        reasonNombre: String,
        dosisSnapshot: String?,
        fotoUrl: String?,
    ): String {
        val duplicate = collection()
            .whereEqualTo("nregistro", nregistro)
            .whereEqualTo("reasonId", reasonId)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
        if (duplicate != null) return duplicate.id

        // La entrada y su cuenta en el resumen van en el mismo lote: o entran las dos o ninguna,
        // y así el resumen no puede quedarse contando algo que no se llegó a guardar.
        val ref = collection().document()
        val batch = firestore.batch()
        batch.set(
            ref,
            mapOf(
                "nregistro" to nregistro,
                "nombreMedicamento" to nombreMedicamento,
                "reasonId" to reasonId,
                "reasonNombre" to reasonNombre,
                "dosisSnapshot" to dosisSnapshot,
                "fotoUrl" to fotoUrl,
                "timestamp" to FieldValue.serverTimestamp(),
            ),
        )
        batch.summaryChange(nregistro, by = 1, info = HistorySummary.medicationInfo(nombreMedicamento, fotoUrl))
        batch.commit().await()
        return ref.id
    }

    override suspend fun updateReason(entry: TakenEntry, reasonId: String, reasonNombre: String) {
        collection().document(entry.id).update(
            mapOf("reasonId" to reasonId, "reasonNombre" to reasonNombre),
        ).await()
    }

    override suspend fun delete(entry: TakenEntry) {
        val batch = firestore.batch()
        batch.delete(collection().document(entry.id))
        batch.summaryChange(entry.nregistro, by = -1)
        batch.commit().await()
    }

    /**
     * Al añadir se escribe también nombre y foto ([info]); al borrar no se tocan, porque si el
     * contador baja a cero el medicamento ya deja de salir.
     */
    private fun WriteBatch.summaryChange(nregistro: String, by: Long, info: Map<String, Any?>? = null) {
        val change = buildMap<String, Any> {
            put(HistorySummary.NREGISTROS_FIELD, mapOf(nregistro to FieldValue.increment(by)))
            if (info != null) put(HistorySummary.MEDICAMENTOS_FIELD, mapOf(nregistro to info))
        }
        set(summaryDocument(), change, SetOptions.merge())
    }
}

private fun DocumentSnapshot.toTakenEntry(): TakenEntry? {
    if (!exists()) return null
    val nregistro = getString("nregistro") ?: return null
    val nombreMedicamento = getString("nombreMedicamento") ?: return null
    val reasonId = getString("reasonId") ?: return null
    val reasonNombre = getString("reasonNombre") ?: return null
    val timestamp = getTimestamp("timestamp", DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
        ?.toDate()?.time ?: System.currentTimeMillis()
    return TakenEntry(
        id = id,
        nregistro = nregistro,
        nombreMedicamento = nombreMedicamento,
        reasonId = reasonId,
        reasonNombre = reasonNombre,
        timestampMillis = timestamp,
        dosisSnapshot = getString("dosisSnapshot"),
        fotoUrl = getString("fotoUrl"),
    )
}
