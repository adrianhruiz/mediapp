package com.medicapp.data.repository

import android.util.Log
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.medicapp.model.PatientProfile
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.LocalDate

/**
 * El perfil va en el propio documento del usuario, junto al email que se guarda al registrarse:
 * se escucha ese único documento y se escribe fusionando, sin tocar los demás campos.
 */
class FirestoreProfileRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
) : ProfileRepository {

    private fun userDocument() = firestore.collection("users")
        .document(authRepository.currentUserId ?: error("No hay sesión iniciada"))

    override fun observe(): Flow<PatientProfile?> = callbackFlow {
        val registration = userDocument().addSnapshotListener { snapshot, error ->
            if (error != null) {
                // No se manda null: el informe lo tomaría por un perfil sin rellenar y volvería a
                // pedir los datos. Se queda con lo último que llegó.
                Log.w("ProfileRepository", "Profile listener failed", error)
                return@addSnapshotListener
            }
            trySend(snapshot?.toPatientProfile())
        }
        awaitClose { registration.remove() }
    }

    override suspend fun save(profile: PatientProfile) {
        userDocument().set(
            mapOf(
                NAME_FIELD to profile.nombre,
                // ISO (aaaa-mm-dd): se lee igual en cualquier idioma y ordena bien como texto.
                BIRTH_DATE_FIELD to (profile.fechaNacimiento?.toString() ?: FieldValue.delete()),
            ),
            SetOptions.merge(),
        ).await()
    }

    private fun DocumentSnapshot.toPatientProfile(): PatientProfile? {
        val nombre = getString(NAME_FIELD)?.takeIf { it.isNotBlank() } ?: return null
        val fechaNacimiento = getString(BIRTH_DATE_FIELD)?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        return PatientProfile(nombre, fechaNacimiento)
    }

    private companion object {
        const val NAME_FIELD = "nombre"
        const val BIRTH_DATE_FIELD = "fechaNacimiento"
    }
}
