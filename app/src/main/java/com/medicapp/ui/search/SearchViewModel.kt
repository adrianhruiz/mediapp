package com.medicapp.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.remote.CimaUnavailableException
import com.medicapp.data.repository.MedicationHistoryRepository
import com.medicapp.data.repository.MedicationSearchRepository
import com.medicapp.data.repository.ScheduleRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import java.io.IOException

class SearchViewModel(
    private val repository: MedicationSearchRepository,
    private val scheduleRepository: ScheduleRepository,
    private val historyRepository: MedicationHistoryRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    /** Medicamentos con un recordatorio activo. */
    val savedNregistros: StateFlow<Set<String>> = scheduleRepository.getActiveScheduleNregistros()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    /** Medicamentos que ya están en el historial. */
    val historyNregistros: StateFlow<Set<String>> = historyRepository.getMedicationNregistros()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val uiState: StateFlow<SearchUiState> = _query
        .debounce(350)
        .distinctUntilChanged()
        .flatMapLatest { text ->
            flow {
                if (text.isBlank()) {
                    emit(SearchUiState.Idle)
                    return@flow
                }
                emit(SearchUiState.Loading)
                try {
                    val result = repository.search(text)
                    emit(
                        if (result.medications.isEmpty()) {
                            SearchUiState.Empty
                        } else {
                            SearchUiState.Success(result.medications, result.totalFilas)
                        },
                    )
                } catch (e: CimaUnavailableException) {
                    emit(
                        SearchUiState.Error(
                            "El buscador de medicamentos no está disponible ahora mismo. " +
                                "Inténtalo de nuevo en unos minutos.",
                        ),
                    )
                } catch (e: IOException) {
                    emit(SearchUiState.Error("No se pudo conectar. Comprueba tu conexión a internet."))
                } catch (e: Exception) {
                    emit(SearchUiState.Error("Ha ocurrido un error inesperado."))
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState.Idle)

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                SearchViewModel(
                    app.container.medicationSearchRepository,
                    app.container.scheduleRepository,
                    app.container.medicationHistoryRepository,
                )
            }
        }
    }
}
