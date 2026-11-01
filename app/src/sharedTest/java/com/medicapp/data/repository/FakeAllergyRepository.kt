package com.medicapp.data.repository

import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import com.medicapp.model.AtcCode
import com.medicapp.model.PrincipioActivo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine

/**
 * Reparte las mismas ids deterministas que Firestore, para que aquí también sea imposible
 * guardar dos veces la misma alergia.
 */
class FakeAllergyRepository(
    initialAllergies: List<Allergy> = emptyList(),
) : AllergyRepository {

    private val allergiesFlow = MutableStateFlow(initialAllergies)

    /** Cuántas veces se ha escrito en el repositorio, para distinguir el alta suelta del lote. */
    var writeCount = 0
        private set

    val allergies: List<Allergy> get() = allergiesFlow.value

    private val loadError = MutableStateFlow<Throwable?>(null)

    override fun getAll(): Flow<List<Allergy>> = allergiesFlow

    override fun getAllOrThrow(): Flow<List<Allergy>> =
        combine(allergiesFlow, loadError) { allergies, error -> if (error != null) throw error else allergies }

    /** Simula que la escucha de Firestore falla: [getAllOrThrow] lanza el error. */
    fun failLoading(error: Throwable) {
        loadError.value = error
    }

    override suspend fun addPrincipioActivo(id: Int, nombre: String) {
        addPrincipiosActivos(listOf(PrincipioActivo(id = id, nombre = nombre)))
    }

    override suspend fun addFamilia(atcCodigo: String, atcNombre: String) {
        addFamilias(listOf(AtcCode(codigo = atcCodigo, nombre = atcNombre, nivel = 5)))
    }

    override suspend fun addPrincipiosActivos(principiosActivos: List<PrincipioActivo>) {
        save(
            principiosActivos.distinctBy { it.id }.map {
                Allergy(
                    id = principioActivoDocumentId(it.id),
                    tipo = AllergyType.PRINCIPIO_ACTIVO,
                    principioActivoId = it.id,
                    principioActivoNombre = it.nombre,
                )
            },
        )
    }

    override suspend fun addFamilias(familias: List<AtcCode>) {
        save(
            familias.distinctBy { it.codigo }.map {
                Allergy(
                    id = familiaDocumentId(it.codigo),
                    tipo = AllergyType.FAMILIA,
                    atcCodigo = it.codigo,
                    atcNombre = it.nombre,
                )
            },
        )
    }

    private fun save(nuevas: List<Allergy>) {
        writeCount++
        val porId = allergiesFlow.value.associateBy { it.id } + nuevas.associateBy { it.id }
        allergiesFlow.value = porId.values.toList()
    }

    override suspend fun setOfficialEnglishNameIfMissing(allergyId: String, nombreIngles: String) {
        allergiesFlow.value = allergiesFlow.value.map {
            if (it.id == allergyId && it.nombreIngles == null) it.copy(nombreIngles = nombreIngles, nombreInglesManual = false) else it
        }
    }

    override suspend fun setEnglishName(allergyId: String, nombreIngles: String?, manual: Boolean) {
        allergiesFlow.value = allergiesFlow.value.map {
            if (it.id == allergyId) it.copy(nombreIngles = nombreIngles, nombreInglesManual = nombreIngles != null && manual) else it
        }
    }

    override suspend fun delete(allergyId: String) {
        allergiesFlow.value = allergiesFlow.value.filterNot { it.id == allergyId }
    }
}
