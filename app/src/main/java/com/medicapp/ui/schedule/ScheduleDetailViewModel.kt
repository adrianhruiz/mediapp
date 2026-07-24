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
import com.medicapp.data.repository.MedicationHistoryRepository
import com.medicapp.data.repository.ScheduleRepository
import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule
import com.medicapp.notifications.DoseAlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ScheduleDetailUiState(
    val schedule: MedicationSchedule? = null,
    val doses: List<MedicationDose> = emptyList(),
)

class ScheduleDetailViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val medicationHistoryRepository: MedicationHistoryRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val scheduleId: String = requireNotNull(savedStateHandle["scheduleId"])

    private val _uiState = MutableStateFlow(ScheduleDetailUiState())
    val uiState: StateFlow<ScheduleDetailUiState> = _uiState.asStateFlow()

    init {
        scheduleRepository.observeSchedule(scheduleId)
            .onEach { schedule -> _uiState.update { it.copy(schedule = schedule) } }
            .launchIn(viewModelScope)

        scheduleRepository.getDoses(scheduleId)
            .onEach { doses -> _uiState.update { it.copy(doses = doses) } }
            .launchIn(viewModelScope)
    }

    fun markDoseTaken(context: Context, dose: MedicationDose) {
        val schedule = _uiState.value.schedule ?: return
        if (dose.status != DoseStatus.PENDING) return
        viewModelScope.launch {
            val historyEntryId = medicationHistoryRepository.add(
                nregistro = schedule.nregistro,
                nombreMedicamento = schedule.nombreMedicamento,
                reasonId = schedule.reasonId,
                reasonNombre = schedule.reasonNombre,
                dosisSnapshot = null,
                fotoUrl = schedule.fotoUrl,
            )
            scheduleRepository.markDoseTaken(scheduleId, dose.id, historyEntryId)
            DoseAlarmScheduler.cancelDose(context.applicationContext, scheduleId, dose.id)
        }
    }

    fun closeSchedule(context: Context) {
        viewModelScope.launch {
            _uiState.value.doses.filter { it.status == DoseStatus.PENDING }.forEach { dose ->
                DoseAlarmScheduler.cancelDose(context.applicationContext, scheduleId, dose.id)
            }
            scheduleRepository.closeSchedule(scheduleId)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                ScheduleDetailViewModel(
                    app.container.scheduleRepository,
                    app.container.medicationHistoryRepository,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
