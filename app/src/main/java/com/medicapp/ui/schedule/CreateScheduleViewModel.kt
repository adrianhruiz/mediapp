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
    val nregistro: String = "",
    val nombreMedicamento: String = "",
    val intervalHoursInput: String = "24",
    /** «Otra» en el control de frecuencia: solo entonces se enseña el campo de horas. */
    val isCustomInterval: Boolean = false,
    /** «Durante N días»; el total de tomas se deriva de aquí con [ScheduleDuration]. */
    val days: Int = 1,
    val reasonInput: String = "",
    /** Motivos ya usados, para elegirlos como chips sin escribirlos. */
    val reasons: List<Reason> = emptyList(),
    val startAtMillis: Long = System.currentTimeMillis(),
    val isSaved: Boolean = false,
    val needsExactAlarmPermission: Boolean = false,
    val hasDuplicateActiveSchedule: Boolean = false,
) {
    val intervalHours: Int? get() = intervalHoursInput.toIntOrNull()?.takeIf { it >= 1 }

    /** Lo que se guarda en la pauta: días × tomas al día, o nada si el intervalo no vale. */
    val totalDoses: Int? get() = intervalHours?.let { ScheduleDuration.totalDoses(days, it) }

    val canSave: Boolean
        get() = reasonInput.isNotBlank() && totalDoses != null && !hasDuplicateActiveSchedule
}

class CreateScheduleViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val reasonRepository: ReasonRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val nregistroArg: String = requireNotNull(savedStateHandle["nregistro"])
    private val nombreArg: String? = savedStateHandle["nombre"]
    private val fotoUrlArg: String? = savedStateHandle["fotoUrl"]

    private val _uiState = MutableStateFlow(CreateScheduleUiState(nregistro = nregistroArg, nombreMedicamento = nombreArg.orEmpty()))
    val uiState: StateFlow<CreateScheduleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            reasonRepository.getAll().collect { reasons ->
                _uiState.update { it.copy(reasons = reasons) }
            }
        }
        viewModelScope.launch {
            val hasDuplicate = scheduleRepository.hasActiveScheduleFor(nregistroArg)
            _uiState.update { it.copy(hasDuplicateActiveSchedule = hasDuplicate) }
        }
    }

    fun onIntervalPresetSelected(hours: Int) {
        _uiState.update { it.copy(intervalHoursInput = hours.toString(), isCustomInterval = false) }
    }

    /** «Otra»: deja el intervalo actual como punto de partida para editarlo a mano. */
    fun onCustomIntervalSelected() {
        _uiState.update { it.copy(isCustomInterval = true) }
    }

    fun onIntervalHoursChanged(value: String) {
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.update { it.copy(intervalHoursInput = value) }
        }
    }

    fun incrementDays() {
        _uiState.update { it.copy(days = (it.days + 1).coerceAtMost(ScheduleDuration.MAX_DAYS)) }
    }

    fun decrementDays() {
        _uiState.update { it.copy(days = (it.days - 1).coerceAtLeast(1)) }
    }

    fun onReasonInputChanged(newValue: String) {
        _uiState.update { it.copy(reasonInput = newValue) }
    }

    fun onReasonSelected(reason: Reason) {
        _uiState.update { it.copy(reasonInput = reason.nombre) }
    }

    fun onStartAtChanged(millis: Long) {
        _uiState.update { it.copy(startAtMillis = millis) }
    }

    fun dismissExactAlarmPrompt() {
        _uiState.update { it.copy(needsExactAlarmPermission = false) }
    }

    fun save(context: Context) {
        val state = _uiState.value
        if (!state.canSave) return
        val intervalHours = state.intervalHours ?: return
        val totalDoses = state.totalDoses ?: return

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
