package com.medicapp.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.repository.MedicationHistoryRepository
import com.medicapp.data.repository.PagedResult
import com.medicapp.data.repository.PagingDefaults
import com.medicapp.data.repository.ReasonRepository
import com.medicapp.model.Reason
import com.medicapp.model.TakenEntry
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HistoryViewModel(
    private val repository: MedicationHistoryRepository,
    reasonRepository: ReasonRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedReasonId = MutableStateFlow<String?>(null)
    val selectedReasonId: StateFlow<String?> = _selectedReasonId.asStateFlow()

    val reasons: StateFlow<List<Reason>> = reasonRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _limit = MutableStateFlow(PagingDefaults.PAGE_SIZE)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val historyPage: StateFlow<PagedResult<TakenEntry>> = _limit
        .flatMapLatest { limit -> repository.getPage(limit) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PagedResult(items = emptyList(), hasMore = true, isFromCache = false),
        )

    private val allEntries: StateFlow<List<TakenEntry>> = historyPage
        .map { it.items }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasMore: StateFlow<Boolean> = historyPage
        .map { it.hasMore }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val isOffline: StateFlow<Boolean> = historyPage
        .map { it.isFromCache }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val entries: StateFlow<List<TakenEntry>> = combine(
        allEntries,
        _query,
        _selectedReasonId,
    ) { entries, query, reasonId ->
        val trimmed = query.trim()
        entries
            .filter { reasonId == null || it.reasonId == reasonId }
            .filter {
                trimmed.isBlank() ||
                    it.nombreMedicamento.contains(trimmed, ignoreCase = true) ||
                    it.reasonNombre.contains(trimmed, ignoreCase = true)
            }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasAnyEntries: StateFlow<Boolean> = allEntries
        .map { it.isNotEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun onReasonFilterChanged(reasonId: String?) {
        _selectedReasonId.value = reasonId
    }

    fun delete(entry: TakenEntry) {
        viewModelScope.launch {
            repository.delete(entry)
        }
    }

    fun loadMore() {
        if (historyPage.value.hasMore) {
            _limit.update { it + PagingDefaults.PAGE_INCREMENT }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                HistoryViewModel(app.container.medicationHistoryRepository, app.container.reasonRepository)
            }
        }
    }
}
