package com.medicapp.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.SetOptions
import com.google.firebase.firestore.WriteBatch
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
     * copia— se rehace una sola vez leyendo el historial completo, que es justo lo que antes se
     * hacía en cada consulta.
     */
    override fun getMedicationNregistros(): Flow<Set<String>> = callbackFlow {
        val registration = summaryDocument().addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("MedicationHistoryRepository", "Medication summary listener failed", error)
                trySend(emptySet())
                return@addSnapshotListener
            }
            @Suppress("UNCHECKED_CAST")
            val counts = snapshot?.get(HistorySummary.NREGISTROS_FIELD) as? Map<String, Any?>
            trySend(HistorySummary.nregistrosWithEntries(counts))

            // Sin respuesta del servidor no sabemos si el resumen falta o es que aún no ha
            // llegado; rehacerlo con esa duda escribiría un resumen a medias.
            val missing = snapshot?.exists() != true && snapshot?.metadata?.isFromCache == false
            if (missing) launch { rebuildSummary() }
        }
        awaitClose { registration.remove() }
    }

    private suspend fun rebuildSummary() {
        if (!rebuildingSummary.compareAndSet(false, true)) return
        try {
            val nregistros = collection().get().await().documents.mapNotNull { it.getString("nregistro") }
            summaryDocument()
                .set(mapOf(HistorySummary.NREGISTROS_FIELD to HistorySummary.countByNregistro(nregistros)))
                .await()
        } catch (e: Exception) {
            // Si falla, el resumen sigue sin existir y volverá a intentarse; mientras tanto la
            // búsqueda solo pierde los distintivos de "ya en tu historial".
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
        batch.summaryChange(nregistro, by = 1)
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

    private fun WriteBatch.summaryChange(nregistro: String, by: Long) {
        set(
            summaryDocument(),
            mapOf(HistorySummary.NREGISTROS_FIELD to mapOf(nregistro to FieldValue.increment(by))),
            SetOptions.merge(),
        )
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
