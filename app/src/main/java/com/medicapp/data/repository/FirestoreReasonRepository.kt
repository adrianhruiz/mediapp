package com.medicapp.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.medicapp.model.Reason
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreReasonRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
) : ReasonRepository {

    private fun collection() = firestore.collection("users")
        .document(authRepository.currentUserId ?: error("No hay sesión iniciada"))
        .collection("reasons")

    override fun getAll(): Flow<List<Reason>> = callbackFlow {
        val registration = collection()
            .orderBy("nombre")
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w("ReasonRepository", "Reasons listener failed", error)
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                trySend(
                    snapshot?.documents.orEmpty().mapNotNull { doc ->
                        doc.getString("nombre")?.let { Reason(id = doc.id, nombre = it) }
                    },
                )
            }
        awaitClose { registration.remove() }
    }

    override suspend fun findOrCreate(nombre: String): Reason {
        val trimmed = nombre.trim()
        val existing = collection()
            .whereEqualTo("nombre", trimmed)
            .limit(1)
            .get()
            .await()
            .documents
            .firstOrNull()
        if (existing != null) return Reason(id = existing.id, nombre = trimmed)
        val ref = collection()
            .add(mapOf("nombre" to trimmed, "createdAt" to FieldValue.serverTimestamp()))
            .await()
        return Reason(id = ref.id, nombre = trimmed)
    }
}
