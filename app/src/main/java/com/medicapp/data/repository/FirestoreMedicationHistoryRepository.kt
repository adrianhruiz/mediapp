package com.medicapp.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.medicapp.model.TakenEntry
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreMedicationHistoryRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
) : MedicationHistoryRepository {

    private fun collection() = firestore.collection("users")
        .document(authRepository.currentUserId ?: error("No hay sesión iniciada"))
        .collection("takenMedications")

    override fun getPage(limit: Long): Flow<PagedResult<TakenEntry>> = callbackFlow {
        val registration = collection()
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .limit(limit)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("MedicationHistoryRepository", "Taken medications listener failed", error)
                    trySend(PagedResult(emptyList(), hasMore = false, isFromCache = false))
                    return@addSnapshotListener
                }
                val items = snapshot?.documents.orEmpty().mapNotNull { it.toTakenEntry() }
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

        val ref = collection().add(
            mapOf(
                "nregistro" to nregistro,
                "nombreMedicamento" to nombreMedicamento,
                "reasonId" to reasonId,
                "reasonNombre" to reasonNombre,
                "dosisSnapshot" to dosisSnapshot,
                "fotoUrl" to fotoUrl,
                "timestamp" to FieldValue.serverTimestamp(),
            ),
        ).await()
        return ref.id
    }

    override suspend fun updateReason(entry: TakenEntry, reasonId: String, reasonNombre: String) {
        collection().document(entry.id).update(
            mapOf("reasonId" to reasonId, "reasonNombre" to reasonNombre),
        ).await()
    }

    override suspend fun delete(entry: TakenEntry) {
        collection().document(entry.id).delete().await()
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
