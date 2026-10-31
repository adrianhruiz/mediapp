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
import com.medicapp.domain.DoseInterval
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

/** Frecuencias de un toque en el control «8 h · 12 h · 24 h · Otra»; el resto se escribe con «Otra». */
val QUICK_INTERVAL_HOURS: List<Int> = listOf(8, 12, 24)

data class CreateScheduleUiState(
    val nregistro: String = "",
    val nombreMedicamento: String = "",
    /** Cantidad del intervalo tal y como se teclea; con [intervalUnit] se convierte a horas. */
    val intervalAmountInput: String = "1",
    val intervalUnit: DoseInterval.Unit = DoseInterval.Unit.DAYS,
    /** Pauta sin fin: no pide duración y dura hasta que se cierre. */
    val openEnded: Boolean = false,
    /** «Otra» en el control de frecuencia: solo entonces se enseña el campo cantidad + unidad. */
    val isCustomInterval: Boolean = false,
    /** «Durante N días»; el total de tomas se deriva de aquí con [ScheduleDuration]. */
    val days: Int = 1,
    /**
     * Total con el que llega la pauta que se edita. Se respeta tal cual hasta que se toca la
     * frecuencia o la duración: así editar solo el motivo no cambia el número de tomas.
     */
    val loadedTotalDoses: Int? = null,
    val reasonInput: String = "",
    /** Motivos ya usados, para elegirlos como chips sin escribirlos. */
    val reasons: List<Reason> = emptyList(),
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
) {
    /** Intervalo en horas que se guardaría, o null si la cantidad está vacía, es cero o desborda. */
    val intervalHours: Int?
        get() = intervalAmountInput.toIntOrNull()
            ?.takeIf { it > 0 }
            ?.let { DoseInterval.toHours(it, intervalUnit) }

    /** Lo que se guarda en la pauta: días × tomas al día, o nada si el intervalo no vale. */
    val totalDoses: Int?
        get() = loadedTotalDoses ?: intervalHours?.let { ScheduleDuration.totalDoses(days, it) }

    val canSave: Boolean
        get() = reasonInput.isNotBlank() && totalDoses != null && !hasDuplicateActiveSchedule
}

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

    /** Evita lanzar dos ediciones con un doble toque en Guardar. */
    private var isSavingEdit = false

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
                _uiState.update { it.copy(reasons = reasons) }
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
        val (intervalAmount, intervalUnit) = DoseInterval.split(schedule.intervalHours)
        val keptDoseCount = ScheduleEditPlanner.keptDoseCount(doses)
        _uiState.update {
            it.copy(
                nregistro = schedule.nregistro,
                nombreMedicamento = schedule.nombreMedicamento,
                intervalAmountInput = intervalAmount.toString(),
                intervalUnit = intervalUnit,
                openEnded = schedule.openEnded,
                // Una frecuencia que no está en el control rápido se enseña abierta en «Otra».
                isCustomInterval = schedule.intervalHours !in QUICK_INTERVAL_HOURS,
                // Una sin fin guarda 0 tomas: si se pasa a duración fija, se parte de un día.
                days = if (schedule.openEnded) 1 else ScheduleDuration.days(schedule.totalDoses, schedule.intervalHours),
                loadedTotalDoses = schedule.totalDoses.takeUnless { schedule.openEnded },
                reasonInput = schedule.reasonNombre,
                startAtMillis = nextDoseAt,
                keptDoseCount = keptDoseCount,
            )
        }
    }

    fun onIntervalPresetSelected(hours: Int) {
        val (amount, unit) = DoseInterval.split(hours)
        _uiState.update {
            it.copy(intervalAmountInput = amount.toString(), intervalUnit = unit, isCustomInterval = false)
                .withRecomputedTotal()
        }
    }

    /** «Otra»: deja el intervalo actual como punto de partida para editarlo a mano. */
    fun onCustomIntervalSelected() {
        _uiState.update { it.copy(isCustomInterval = true) }
    }

    fun onIntervalAmountChanged(value: String) {
        if (value.isEmpty() || value.all { it.isDigit() }) {
            _uiState.update { it.copy(intervalAmountInput = value).withRecomputedTotal() }
        }
    }

    fun onIntervalUnitChanged(unit: DoseInterval.Unit) {
        _uiState.update { it.copy(intervalUnit = unit).withRecomputedTotal() }
    }

    fun incrementDays() {
        _uiState.update { it.copy(days = (it.days + 1).coerceAtMost(ScheduleDuration.MAX_DAYS)).withRecomputedTotal() }
    }

    fun onOpenEndedChanged(openEnded: Boolean) {
        _uiState.update { it.copy(openEnded = openEnded, totalBelowTaken = false) }
    }

    fun decrementDays() {
        _uiState.update { it.copy(days = (it.days - 1).coerceAtLeast(1)).withRecomputedTotal() }
    }

    /** Tocar frecuencia o duración vuelve a calcular el total y retira el aviso de total bajo. */
    private fun CreateScheduleUiState.withRecomputedTotal() = copy(loadedTotalDoses = null, totalBelowTaken = false)

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
        // Sin fin no hay total que validar: el repositorio lo guarda como 0.
        val totalDoses = if (state.openEnded) 0 else state.totalDoses ?: return
        if (!state.openEnded && totalDoses <= state.keptDoseCount) {
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
                openEnded = state.openEnded,
                nowMillis = now(),
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
                    openEnded = state.openEnded,
                    nowMillis = now(),
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
