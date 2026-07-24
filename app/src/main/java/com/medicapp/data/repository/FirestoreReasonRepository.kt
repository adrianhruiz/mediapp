package com.medicapp.data.repository

import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.medicapp.model.Reason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreReasonRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    scope: CoroutineScope,
) : ReasonRepository {

    private fun collection() = collectionFor(authRepository.currentUserId ?: error("No hay sesión iniciada"))

    private fun collectionFor(userId: String) = firestore.collection("users")
        .document(userId)
        .collection("reasons")

    /** Lo miran a la vez el historial, añadir/editar entrada y crear recordatorio. */
    private val reasons: Flow<List<Reason>> =
        authRepository.sharedPerSession(scope, emptyValue = emptyList()) { userId ->
            callbackFlow {
                val registration = collectionFor(userId)
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
        }

    override fun getAll(): Flow<List<Reason>> = reasons

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
