package com.medicapp.data.repository

import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeAllergyRepository(
    initialAllergies: List<Allergy> = emptyList(),
) : AllergyRepository {

    private val allergiesFlow = MutableStateFlow(initialAllergies)
    private var nextId = 1

    val allergies: List<Allergy> get() = allergiesFlow.value

    override fun getAll(): Flow<List<Allergy>> = allergiesFlow

    override suspend fun addPrincipioActivo(id: Int, nombre: String) {
        val exists = allergiesFlow.value.any {
            it.tipo == AllergyType.PRINCIPIO_ACTIVO && it.principioActivoId == id
        }
        if (exists) return
        allergiesFlow.value = allergiesFlow.value + Allergy(
            id = "allergy-${nextId++}",
            tipo = AllergyType.PRINCIPIO_ACTIVO,
            principioActivoId = id,
            principioActivoNombre = nombre,
        )
    }

    override suspend fun addFamilia(atcCodigo: String, atcNombre: String) {
        val exists = allergiesFlow.value.any { it.tipo == AllergyType.FAMILIA && it.atcCodigo == atcCodigo }
        if (exists) return
        allergiesFlow.value = allergiesFlow.value + Allergy(
            id = "allergy-${nextId++}",
            tipo = AllergyType.FAMILIA,
            atcCodigo = atcCodigo,
            atcNombre = atcNombre,
        )
    }

    override suspend fun delete(allergyId: String) {
        allergiesFlow.value = allergiesFlow.value.filterNot { it.id == allergyId }
    }
}
