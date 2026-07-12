package com.medicapp.ui.search

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.repository.AllergyRepository
import com.medicapp.data.repository.MedicationCatalogRepository
import com.medicapp.domain.AllergyChecker
import com.medicapp.model.Allergy
import com.medicapp.model.AllergyMatch
import com.medicapp.model.AllergyType
import com.medicapp.model.AtcCode
import com.medicapp.model.MedicationDetail
import com.medicapp.model.PrincipioActivo
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MedicationDetailUiState(
    val isLoading: Boolean = true,
    val medication: MedicationDetail? = null,
    val allergies: List<Allergy> = emptyList(),
    val matches: List<AllergyMatch> = emptyList(),
    val errorMessage: String? = null,
) {
    /** Un medicamento desrecomendado por alergia no puede añadirse al historial. */
    val canAddToHistory: Boolean get() = medication != null && matches.isEmpty()
}

class MedicationDetailViewModel(
    private val catalogRepository: MedicationCatalogRepository,
    private val allergyRepository: AllergyRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val nregistro: String = requireNotNull(savedStateHandle["nregistro"])

    private val _uiState = MutableStateFlow(MedicationDetailUiState())
    val uiState: StateFlow<MedicationDetailUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            try {
                val detail = catalogRepository.getMedicationDetail(nregistro)
                _uiState.update { it.copy(medication = detail, isLoading = false) }
                recomputeMatches()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(isLoading = false, errorMessage = "No se pudo cargar el medicamento.")
                }
            }
        }
        allergyRepository.getAll()
            .onEach { allergies ->
                _uiState.update { it.copy(allergies = allergies) }
                recomputeMatches()
            }
            .launchIn(viewModelScope)
    }

    private fun recomputeMatches() {
        val state = _uiState.value
        val medication = state.medication ?: return
        _uiState.update { it.copy(matches = AllergyChecker.findMatches(medication, state.allergies)) }
    }

    fun toggleIngredientAllergy(principioActivo: PrincipioActivo) {
        viewModelScope.launch {
            val existing = _uiState.value.allergies.firstOrNull {
                it.tipo == AllergyType.PRINCIPIO_ACTIVO && it.principioActivoId == principioActivo.id
            }
            if (existing != null) {
                allergyRepository.delete(existing.id)
            } else {
                allergyRepository.addPrincipioActivo(principioActivo.id, principioActivo.nombre)
            }
        }
    }

    fun toggleFamiliaAllergy(atc: AtcCode) {
        viewModelScope.launch {
            val existing = _uiState.value.allergies.firstOrNull {
                it.tipo == AllergyType.FAMILIA && it.atcCodigo == atc.codigo
            }
            if (existing != null) {
                allergyRepository.delete(existing.id)
            } else {
                allergyRepository.addFamilia(atc.codigo, atc.nombre)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                MedicationDetailViewModel(
                    app.container.medicationCatalogRepository,
                    app.container.allergyRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
