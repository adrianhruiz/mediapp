package com.medicapp.data.repository

import com.medicapp.model.TakenEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map

class FakeMedicationHistoryRepository(
    initialEntries: List<TakenEntry> = emptyList(),
) : MedicationHistoryRepository {

    private val entriesFlow = MutableStateFlow(initialEntries)
    private var nextId = initialEntries.size + 1
    var clockMillis: Long = 0L

    val entries: List<TakenEntry> get() = entriesFlow.value

    override fun getPage(limit: Long): Flow<PagedResult<TakenEntry>> = entriesFlow.map { entries ->
        val page = entries.take(limit.toInt())
        PagedResult(items = page, hasMore = page.size.toLong() >= limit, isFromCache = false)
    }

    override fun getByMedication(nregistro: String): Flow<List<TakenEntry>> = entriesFlow.map { entries ->
        entries.filter { it.nregistro == nregistro }
    }

    override suspend fun getById(id: String): TakenEntry? = entriesFlow.value.firstOrNull { it.id == id }

    override suspend fun add(
        nregistro: String,
        nombreMedicamento: String,
        reasonId: String,
        reasonNombre: String,
        dosisSnapshot: String?,
        fotoUrl: String?,
    ): String {
        val duplicate = entriesFlow.value.firstOrNull { it.nregistro == nregistro && it.reasonId == reasonId }
        if (duplicate != null) return duplicate.id

        val entry = TakenEntry(
            id = "entry-${nextId++}",
            nregistro = nregistro,
            nombreMedicamento = nombreMedicamento,
            reasonId = reasonId,
            reasonNombre = reasonNombre,
            timestampMillis = clockMillis,
            dosisSnapshot = dosisSnapshot,
            fotoUrl = fotoUrl,
        )
        entriesFlow.value = listOf(entry) + entriesFlow.value
        return entry.id
    }

    override suspend fun updateReason(entry: TakenEntry, reasonId: String, reasonNombre: String) {
        entriesFlow.value = entriesFlow.value.map {
            if (it.id == entry.id) it.copy(reasonId = reasonId, reasonNombre = reasonNombre) else it
        }
    }

    override suspend fun delete(entry: TakenEntry) {
        entriesFlow.value = entriesFlow.value.filterNot { it.id == entry.id }
    }
}
