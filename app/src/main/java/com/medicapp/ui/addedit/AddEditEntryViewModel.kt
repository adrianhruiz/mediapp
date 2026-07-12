package com.medicapp.ui.addedit

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.repository.MedicationHistoryRepository
import com.medicapp.data.repository.ReasonRepository
import com.medicapp.model.Reason
import com.medicapp.model.TakenEntry
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AddEditUiState(
    val nombreMedicamento: String = "",
    val reasonInput: String = "",
    val suggestions: List<Reason> = emptyList(),
    val isEditMode: Boolean = false,
    val isSaved: Boolean = false,
    val isLoading: Boolean = true,
    /** Motivos con los que este medicamento ya está en el historial, sin contar la entrada en edición. */
    val existingReasonNombres: List<String> = emptyList(),
) {
    /** El mismo medicamento con el mismo motivo ya está en el historial. */
    val isDuplicate: Boolean
        get() = existingReasonNombres.any { it.equals(reasonInput.trim(), ignoreCase = true) }

    val canSave: Boolean get() = reasonInput.isNotBlank() && !isDuplicate
}

private const val MAX_SUGGESTIONS = 5

class AddEditEntryViewModel(
    private val historyRepository: MedicationHistoryRepository,
    private val reasonRepository: ReasonRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val nregistroArg: String? = savedStateHandle["nregistro"]
    private val nombreArg: String? = savedStateHandle["nombre"]
    private val fotoUrlArg: String? = savedStateHandle["fotoUrl"]
    private val entryIdArg: String? = savedStateHandle["entryId"]

    private var originalEntry: TakenEntry? = null
    private var allReasons: List<Reason> = emptyList()

    private val _uiState = MutableStateFlow(AddEditUiState())
    val uiState: StateFlow<AddEditUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            reasonRepository.getAll().collect { reasons ->
                allReasons = reasons
                recomputeSuggestions()
            }
        }
        if (entryIdArg != null) {
            viewModelScope.launch {
                val entry = historyRepository.getById(entryIdArg)
                originalEntry = entry
                _uiState.update {
                    it.copy(
                        nombreMedicamento = entry?.nombreMedicamento.orEmpty(),
                        reasonInput = entry?.reasonNombre.orEmpty(),
                        isEditMode = true,
                        isLoading = false,
                    )
                }
                entry?.let { observeExistingEntries(it.nregistro) }
            }
        } else {
            _uiState.update {
                it.copy(
                    nombreMedicamento = nombreArg.orEmpty(),
                    isEditMode = false,
                    isLoading = false,
                )
            }
            nregistroArg?.let { observeExistingEntries(it) }
        }
    }

    private fun observeExistingEntries(nregistro: String) {
        historyRepository.getByMedication(nregistro)
            .onEach { entries ->
                val reasons = entries.filter { it.id != entryIdArg }.map { it.reasonNombre }
                _uiState.update { it.copy(existingReasonNombres = reasons) }
            }
            .launchIn(viewModelScope)
    }

    private fun recomputeSuggestions() {
        val query = _uiState.value.reasonInput.trim()
        val filtered = if (query.isBlank()) {
            allReasons
        } else {
            allReasons.filter { it.nombre.contains(query, ignoreCase = true) }
        }
        _uiState.update { it.copy(suggestions = filtered.take(MAX_SUGGESTIONS)) }
    }

    fun onReasonInputChanged(newValue: String) {
        _uiState.update { it.copy(reasonInput = newValue) }
        recomputeSuggestions()
    }

    fun onSuggestionSelected(reason: Reason) {
        _uiState.update { it.copy(reasonInput = reason.nombre, suggestions = emptyList()) }
    }

    fun save() {
        val state = _uiState.value
        if (!state.canSave) return
        viewModelScope.launch {
            val reason = reasonRepository.findOrCreate(state.reasonInput)
            val entry = originalEntry
            if (state.isEditMode && entry != null) {
                historyRepository.updateReason(entry, reason.id, reason.nombre)
            } else if (nregistroArg != null) {
                historyRepository.add(
                    nregistro = nregistroArg,
                    nombreMedicamento = nombreArg.orEmpty(),
                    reasonId = reason.id,
                    reasonNombre = reason.nombre,
                    dosisSnapshot = null,
                    fotoUrl = fotoUrlArg,
                )
            }
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                AddEditEntryViewModel(
                    app.container.medicationHistoryRepository,
                    app.container.reasonRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
