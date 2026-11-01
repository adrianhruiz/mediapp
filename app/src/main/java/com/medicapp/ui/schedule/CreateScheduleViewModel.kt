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
import com.medicapp.domain.ScheduleEditPlanner
import com.medicapp.model.DoseStatus
import com.medicapp.model.Reason
import com.medicapp.notifications.DoseAlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CreateScheduleUiState(
    val nregistro: String = "",
    val nombreMedicamento: String = "",
    val intervalHoursInput: String = "24",
    val totalDosesInput: String = "1",
    val reasonInput: String = "",
    val suggestions: List<Reason> = emptyList(),
    val startAtMillis: Long = System.currentTimeMillis(),
    val isSaved: Boolean = false,
    val needsExactAlarmPermission: Boolean = false,
    val hasDuplicateActiveSchedule: Boolean = false,
    /** Editando una pauta existente en vez de crear una nueva. */
    val isEditing: Boolean = false,
    /** Tomas ya hechas de la pauta que se edita: el total no puede bajar de ahí. */
    val keptDoseCount: Int = 0,
    /** Al guardar, el total no dejaba ninguna toma por hacer. */
    val totalBelowTaken: Boolean = false,
    /** La edición no se pudo guardar (sin conexión o la pauta ya no está activa). */
    val saveFailed: Boolean = false,
)

private const val MAX_SUGGESTIONS = 5

/**
 * Crea una pauta o, si llega `scheduleId`, edita una existente: mismo formulario, con los
 * valores de la pauta cargados y la hora de la próxima toma en lugar de la primera.
 */
class CreateScheduleViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val reasonRepository: ReasonRepository,
    savedStateHandle: SavedStateHandle,
    private val now: () -> Long = System::currentTimeMillis,
) : ViewModel() {

    private val scheduleIdArg: String? = savedStateHandle["scheduleId"]
    private val nregistroArg: String = savedStateHandle["nregistro"] ?: ""
    private val nombreArg: String? = savedStateHandle["nombre"]
    private val fotoUrlArg: String? = savedStateHandle["fotoUrl"]

    private var allReasons: List<Reason> = emptyList()

    /** Evita lanzar dos ediciones con un doble toque en Guardar. */
    private var isSavingEdit = false

    /** Motivo con el que llega la pauta editada: no tiene sentido sugerirlo de vuelta. */
    private var loadedReason: String? = null

    private val _uiState = MutableStateFlow(
        CreateScheduleUiState(
            nregistro = nregistroArg,
            nombreMedicamento = nombreArg.orEmpty(),
            startAtMillis = now(),
            isEditing = scheduleIdArg != null,
        ),
    )
    val uiState: StateFlow<CreateScheduleUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            reasonRepository.getAll().collect { reasons ->
                allReasons = reasons
                recomputeSuggestions()
            }
        }
        if (scheduleIdArg != null) {
            viewModelScope.launch { loadSchedule(scheduleIdArg) }
        } else {
            viewModelScope.launch {
                val hasDuplicate = scheduleRepository.hasActiveScheduleFor(nregistroArg)
                _uiState.update { it.copy(hasDuplicateActiveSchedule = hasDuplicate) }
            }
        }
    }

    /**
     * La hora propuesta es la de la próxima toma pendiente que aún no ha pasado; las pasadas sin
     * marcar se van a sustituir igualmente, así que no sirven de referencia.
     */
    private suspend fun loadSchedule(scheduleId: String) {
        val schedule = scheduleRepository.getSchedule(scheduleId) ?: return
        val doses = scheduleRepository.getDoses(scheduleId).first()
        val currentTime = now()
        val nextDoseAt = doses
            .filter { it.status == DoseStatus.PENDING && it.scheduledAtMillis >= currentTime }
            .minOfOrNull { it.scheduledAtMillis } ?: currentTime
        loadedReason = schedule.reasonNombre
        _uiState.update {
            it.copy(
                nregistro = schedule.nregistro,
                nombreMedicamento = schedule.nombreMedicamento,
                intervalHoursInput = schedule.intervalHours.toString(),
                totalDosesInput = schedule.totalDoses.toString(),
                reasonInput = schedule.reasonNombre,
                startAtMillis = nextDoseAt,
                keptDoseCount = ScheduleEditPlanner.keptDoseCount(doses),
            )
        }
        recomputeSuggestions()
    }

    private fun recomputeSuggestions() {
        val query = _uiState.value.reasonInput.trim()
        val filtered = if (query == loadedReason) {
            emptyList()
        } else if (query.isBlank()) {
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
            _uiState.update { it.copy(totalDosesInput = value, totalBelowTaken = false) }
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
        if (totalDoses <= state.keptDoseCount) {
            _uiState.update { it.copy(totalBelowTaken = true) }
            return
        }

        if (!DoseAlarmScheduler.canScheduleExactAlarms(context)) {
            _uiState.update { it.copy(needsExactAlarmPermission = true) }
            return
        }

        val appContext = context.applicationContext
        if (scheduleIdArg != null) {
            saveEdit(appContext, scheduleIdArg, intervalHours, totalDoses)
        } else {
            saveNew(appContext, intervalHours, totalDoses)
        }
    }

    private fun saveNew(appContext: Context, intervalHours: Int, totalDoses: Int) {
        val state = _uiState.value
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

    /** Las alarmas se mueven solo si Firestore confirma la edición, que necesita conexión. */
    private fun saveEdit(appContext: Context, scheduleId: String, intervalHours: Int, totalDoses: Int) {
        if (isSavingEdit) return
        isSavingEdit = true
        val state = _uiState.value
        _uiState.update { it.copy(saveFailed = false) }
        viewModelScope.launch {
            val result = runCatching {
                val reason = reasonRepository.findOrCreate(state.reasonInput)
                scheduleRepository.updateSchedule(
                    scheduleId = scheduleId,
                    intervalHours = intervalHours,
                    totalDoses = totalDoses,
                    nextDoseAtMillis = state.startAtMillis,
                    reasonId = reason.id,
                    reasonNombre = reason.nombre,
                )
            }.getOrNull()
            if (result == null) {
                isSavingEdit = false
                _uiState.update { it.copy(saveFailed = true) }
                return@launch
            }
            result.removedDoseIds.forEach { DoseAlarmScheduler.cancelDose(appContext, scheduleId, it) }
            result.newDoses.forEach { dose ->
                DoseAlarmScheduler.scheduleDose(
                    appContext,
                    scheduleId,
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
