package com.medicapp.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirestoreAllergyRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
) : AllergyRepository {

    private fun collection() = firestore.collection("users")
        .document(authRepository.currentUserId ?: error("No hay sesión iniciada"))
        .collection("allergies")

    override fun getAll(): Flow<List<Allergy>> = callbackFlow {
        val registration = collection().addSnapshotListener { snapshot, error ->
            if (error != null) {
                Log.w("AllergyRepository", "Allergies listener failed", error)
                trySend(emptyList())
                return@addSnapshotListener
            }
            trySend(snapshot?.documents.orEmpty().mapNotNull { it.toAllergy() })
        }
        awaitClose { registration.remove() }
    }

    override suspend fun addPrincipioActivo(id: Int, nombre: String) {
        if (exists("principioActivoId", id, AllergyType.PRINCIPIO_ACTIVO)) return
        collection().add(
            mapOf(
                "tipo" to AllergyType.PRINCIPIO_ACTIVO.name,
                "principioActivoId" to id,
                "principioActivoNombre" to nombre,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    override suspend fun addFamilia(atcCodigo: String, atcNombre: String) {
        if (exists("atcCodigo", atcCodigo, AllergyType.FAMILIA)) return
        collection().add(
            mapOf(
                "tipo" to AllergyType.FAMILIA.name,
                "atcCodigo" to atcCodigo,
                "atcNombre" to atcNombre,
                "createdAt" to FieldValue.serverTimestamp(),
            ),
        ).await()
    }

    /** La misma alergia no puede guardarse dos veces, aunque se pulse repetidamente. */
    private suspend fun exists(field: String, value: Any, tipo: AllergyType): Boolean =
        collection()
            .whereEqualTo("tipo", tipo.name)
            .whereEqualTo(field, value)
            .limit(1)
            .get()
            .await()
            .isEmpty
            .not()

    override suspend fun delete(allergyId: String) {
        collection().document(allergyId).delete().await()
    }
}

private fun DocumentSnapshot.toAllergy(): Allergy? {
    val tipoRaw = getString("tipo") ?: return null
    val tipo = runCatching { AllergyType.valueOf(tipoRaw) }.getOrNull() ?: return null
    return Allergy(
        id = id,
        tipo = tipo,
        principioActivoId = getLong("principioActivoId")?.toInt(),
        principioActivoNombre = getString("principioActivoNombre"),
        atcCodigo = getString("atcCodigo"),
        atcNombre = getString("atcNombre"),
    )
}
