package com.medicapp.data.repository

import android.util.Log
import com.google.firebase.firestore.CollectionReference
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.QuerySnapshot
import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import com.medicapp.model.AtcCode
import com.medicapp.model.PrincipioActivo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/** Un lote de Firestore admite 500 operaciones; cada reescritura de id gasta dos (alta y baja). */
private const val FIRESTORE_BATCH_LIMIT = 500
private const val REWRITES_PER_BATCH = FIRESTORE_BATCH_LIMIT / 2

class FirestoreAllergyRepository(
    private val firestore: FirebaseFirestore,
    private val authRepository: AuthRepository,
    scope: CoroutineScope,
) : AllergyRepository {

    private fun collection() = collectionFor(authRepository.currentUserId ?: error("No hay sesión iniciada"))

    private fun collectionFor(userId: String) = firestore.collection("users")
        .document(userId)
        .collection("allergies")

    /** Lo miran a la vez la pantalla de alergias y la ficha de medicamento. */
    private val allergies: Flow<List<Allergy>> =
        authRepository.sharedPerSession(scope, emptyValue = emptyList()) { userId ->
            callbackFlow {
                val collection = collectionFor(userId)
                val registration = collection.addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.w("AllergyRepository", "Allergies listener failed", error)
                        trySend(emptyList())
                        return@addSnapshotListener
                    }
                    trySend(snapshot?.documents.orEmpty().mapNotNull { it.toAllergy() })
                    if (snapshot != null) migrateLegacyIds(snapshot, collection)
                }
                awaitClose { registration.remove() }
            }
        }

    override fun getAll(): Flow<List<Allergy>> = allergies

    override suspend fun addPrincipioActivo(id: Int, nombre: String) {
        collection().document(principioActivoDocumentId(id)).set(principioActivoData(id, nombre)).await()
    }

    override suspend fun addFamilia(atcCodigo: String, atcNombre: String) {
        collection().document(familiaDocumentId(atcCodigo)).set(familiaData(atcCodigo, atcNombre)).await()
    }

    /**
     * CIMA devuelve varios nombres para el mismo principio activo —sales y sinónimos comparten
     * identificador—, así que la lista trae repetidos que caen en el mismo documento. Se queda
     * el primero, que es el que encabeza los resultados en pantalla.
     */
    override suspend fun addPrincipiosActivos(principiosActivos: List<PrincipioActivo>) {
        writeInBatches(
            principiosActivos.distinctBy { it.id }.associate {
                principioActivoDocumentId(it.id) to principioActivoData(it.id, it.nombre)
            },
        )
    }

    override suspend fun addFamilias(familias: List<AtcCode>) {
        writeInBatches(
            familias.distinctBy { it.codigo }.associate {
                familiaDocumentId(it.codigo) to familiaData(it.codigo, it.nombre)
            },
        )
    }

    private suspend fun writeInBatches(documents: Map<String, Map<String, Any?>>) {
        val collection = collection()
        documents.entries.chunked(FIRESTORE_BATCH_LIMIT).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (id, data) -> batch.set(collection.document(id), data) }
            batch.commit().await()
        }
    }

    override suspend fun delete(allergyId: String) {
        collection().document(allergyId).delete().await()
    }

    /**
     * Pasa a la id determinista las alergias que se guardaron cuando la sorteaba Firestore.
     *
     * Se hace desde el propio listener porque ahí ya tenemos —y ya hemos pagado— la lista
     * entera, así que no cuesta ninguna lectura de más. Solo con la respuesta confirmada por el
     * servidor y sin escrituras a medio confirmar: sobre una foto incompleta podríamos borrar un
     * documento cuyo reemplazo aún no ha llegado.
     *
     * No se espera al resultado: si el lote falla, la próxima vez que se mire la lista se vuelve
     * a intentar, y mientras tanto la app funciona igual con las ids viejas.
     */
    private fun migrateLegacyIds(snapshot: QuerySnapshot, collection: CollectionReference) {
        if (snapshot.metadata.isFromCache || snapshot.metadata.hasPendingWrites()) return
        val rewrites = planAllergyIdMigration(snapshot.documents.mapNotNull { it.toAllergy() })
        if (rewrites.isEmpty()) return

        val dataById = snapshot.documents.associate { it.id to it.data }
        rewrites.entries.chunked(REWRITES_PER_BATCH).forEach { chunk ->
            val batch = firestore.batch()
            chunk.forEach { (oldId, newId) ->
                val data = dataById[oldId] ?: return@forEach
                batch.set(collection.document(newId), data)
                batch.delete(collection.document(oldId))
            }
            batch.commit().addOnFailureListener { error ->
                Log.w("AllergyRepository", "No se pudieron migrar las ids de alergias", error)
            }
        }
    }
}

private fun principioActivoData(id: Int, nombre: String) = mapOf(
    "tipo" to AllergyType.PRINCIPIO_ACTIVO.name,
    "principioActivoId" to id,
    "principioActivoNombre" to nombre,
    "createdAt" to FieldValue.serverTimestamp(),
)

private fun familiaData(atcCodigo: String, atcNombre: String) = mapOf(
    "tipo" to AllergyType.FAMILIA.name,
    "atcCodigo" to atcCodigo,
    "atcNombre" to atcNombre,
    "createdAt" to FieldValue.serverTimestamp(),
)

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
