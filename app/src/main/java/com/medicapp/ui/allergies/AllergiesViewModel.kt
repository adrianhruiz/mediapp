package com.medicapp.ui.allergies

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.repository.AllergyRepository
import com.medicapp.data.repository.MedicationCatalogRepository
import com.medicapp.model.Allergy
import com.medicapp.model.AtcCode
import com.medicapp.model.PrincipioActivo
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.IOException

enum class AllergySearchMode { PRINCIPIO_ACTIVO, FAMILIA }

sealed interface CatalogSearchUiState {
    data object Idle : CatalogSearchUiState
    data object Loading : CatalogSearchUiState
    data object Empty : CatalogSearchUiState
    data class Error(val message: String) : CatalogSearchUiState
    data class IngredientResults(val items: List<PrincipioActivo>) : CatalogSearchUiState
    data class FamilyResults(val items: List<AtcCode>) : CatalogSearchUiState
}

class AllergiesViewModel(
    private val allergyRepository: AllergyRepository,
    private val catalogRepository: MedicationCatalogRepository,
) : ViewModel() {

    val allergies: StateFlow<List<Allergy>> = allergyRepository.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _searchMode = MutableStateFlow(AllergySearchMode.PRINCIPIO_ACTIVO)
    val searchMode: StateFlow<AllergySearchMode> = _searchMode.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
    val searchState: StateFlow<CatalogSearchUiState> =
        combine(_query.debounce(350), _searchMode) { text, mode -> text to mode }
            .distinctUntilChanged()
            .flatMapLatest { (text, mode) ->
                flow {
                    if (text.isBlank()) {
                        emit(CatalogSearchUiState.Idle)
                        return@flow
                    }
                    emit(CatalogSearchUiState.Loading)
                    try {
                        when (mode) {
                            AllergySearchMode.PRINCIPIO_ACTIVO -> {
                                val results = catalogRepository.searchPrincipiosActivos(text)
                                emit(
                                    if (results.isEmpty()) {
                                        CatalogSearchUiState.Empty
                                    } else {
                                        CatalogSearchUiState.IngredientResults(results)
                                    },
                                )
                            }
                            AllergySearchMode.FAMILIA -> {
                                val results = catalogRepository.searchAtcCodes(text)
                                emit(
                                    if (results.isEmpty()) {
                                        CatalogSearchUiState.Empty
                                    } else {
                                        CatalogSearchUiState.FamilyResults(results)
                                    },
                                )
                            }
                        }
                    } catch (e: IOException) {
                        emit(CatalogSearchUiState.Error("No se pudo conectar. Comprueba tu conexión a internet."))
                    } catch (e: Exception) {
                        emit(CatalogSearchUiState.Error("Ha ocurrido un error inesperado."))
                    }
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), CatalogSearchUiState.Idle)

    fun onSearchModeChanged(mode: AllergySearchMode) {
        _searchMode.value = mode
    }

    fun onQueryChanged(newQuery: String) {
        _query.value = newQuery
    }

    fun addIngredientAllergy(principioActivo: PrincipioActivo) {
        viewModelScope.launch { allergyRepository.addPrincipioActivo(principioActivo.id, principioActivo.nombre) }
    }

    fun addFamilyAllergy(atc: AtcCode) {
        viewModelScope.launch { allergyRepository.addFamilia(atc.codigo, atc.nombre) }
    }

    fun delete(allergy: Allergy) {
        viewModelScope.launch { allergyRepository.delete(allergy.id) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                AllergiesViewModel(app.container.allergyRepository, app.container.medicationCatalogRepository)
            }
        }
    }
}
