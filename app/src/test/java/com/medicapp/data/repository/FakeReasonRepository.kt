package com.medicapp.data.repository

import com.medicapp.model.Reason
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

class FakeReasonRepository(
    initialReasons: List<Reason> = emptyList(),
) : ReasonRepository {

    private val reasonsFlow = MutableStateFlow(initialReasons)
    private var nextId = initialReasons.size + 1

    val reasons: List<Reason> get() = reasonsFlow.value

    override fun getAll(): Flow<List<Reason>> = reasonsFlow

    override suspend fun findOrCreate(nombre: String): Reason {
        val trimmed = nombre.trim()
        val existing = reasonsFlow.value.firstOrNull { it.nombre == trimmed }
        if (existing != null) return existing
        val reason = Reason(id = "reason-${nextId++}", nombre = trimmed)
        reasonsFlow.value = reasonsFlow.value + reason
        return reason
    }
}
