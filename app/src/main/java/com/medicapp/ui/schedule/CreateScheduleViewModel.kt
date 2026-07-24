package com.medicapp.ui.schedule

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.repository.ReasonRepository
import com.medicapp.data.repository.ScheduleRepository
import com.medicapp.model.Reason
import com.medicapp.notifications.DoseAlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateScheduleUiState(
    val nombreMedicamento: String = "",
    val intervalHoursInput: String = "24",
    val totalDosesInput: String = "1",
    val reasonInput: String = "",
    val suggestions: List<Reason> = emptyList(),
    val startAtMillis: Long = System.currentTimeMillis(),
    val isSaved: Boolean = false,
    val needsExactAlarmPermission: Boolean = false,
    val hasDuplicateActiveSchedule: Boolean = false,
)

private const val MAX_SUGGESTIONS = 5

class CreateScheduleViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val reasonRepository: ReasonRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val nregistroArg: String = requireNotNull(savedStateHandle["nregistro"])
    private val nombreArg: String? = savedStateHandle["nombre"]
    private val fotoUrlArg: String? = savedStateHandle["fotoUrl"]

    private var allReasons: List<Reason> = emptyList()

    private val _uiState = MutableStateFlow(CreateScheduleUiState(nombreMedicamento = nombreArg.orEmpty()))
    val uiState: StateFlow<CreateScheduleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            reasonRepository.getAll().collect { reasons ->
                allReasons = reasons
                recomputeSuggestions()
            }
        }
        viewModelScope.launch {
            val hasDuplicate = scheduleRepository.hasActiveScheduleFor(nregistroArg)
            _uiState.update { it.copy(hasDuplicateActiveSchedule = hasDuplicate) }
        }
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

    fun onIntervalPresetSelected(hours: Int) {
        _uiState.update { it.copy(intervalHoursInput = hours.toString()) }
    }

    fun onIntervalHoursChanged(value: String) {
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.update { it.copy(intervalHoursInput = value) }
        }
    }

    fun onTotalDosesChanged(value: String) {
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.update { it.copy(totalDosesInput = value) }
        }
    }

    fun onReasonInputChanged(newValue: String) {
        _uiState.update { it.copy(reasonInput = newValue) }
        recomputeSuggestions()
    }

    fun onSuggestionSelected(reason: Reason) {
        _uiState.update { it.copy(reasonInput = reason.nombre, suggestions = emptyList()) }
    }

    fun onStartAtChanged(millis: Long) {
        _uiState.update { it.copy(startAtMillis = millis) }
    }

    fun dismissExactAlarmPrompt() {
        _uiState.update { it.copy(needsExactAlarmPermission = false) }
    }

    fun save(context: Context) {
        val state = _uiState.value
        val intervalHours = state.intervalHoursInput.toIntOrNull() ?: return
        val totalDoses = state.totalDosesInput.toIntOrNull() ?: return
        if (intervalHours < 1 || totalDoses < 1 || state.reasonInput.isBlank()) return
        if (state.hasDuplicateActiveSchedule) return

        if (!DoseAlarmScheduler.canScheduleExactAlarms(context)) {
            _uiState.update { it.copy(needsExactAlarmPermission = true) }
            return
        }

        val appContext = context.applicationContext
        viewModelScope.launch {
            val reason = reasonRepository.findOrCreate(state.reasonInput)
            val result = scheduleRepository.createSchedule(
                nregistro = nregistroArg,
                nombreMedicamento = nombreArg.orEmpty(),
                fotoUrl = fotoUrlArg,
                intervalHours = intervalHours,
                totalDoses = totalDoses,
                startAtMillis = state.startAtMillis,
                reasonId = reason.id,
                reasonNombre = reason.nombre,
            )
            result.doses.forEach { dose ->
                DoseAlarmScheduler.scheduleDose(
                    appContext,
                    result.schedule.id,
                    dose,
                    result.schedule.nombreMedicamento,
                    result.schedule.totalDoses,
                )
            }
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                CreateScheduleViewModel(
                    app.container.scheduleRepository,
                    app.container.reasonRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
