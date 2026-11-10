package com.medicapp.ui.schedule

import android.content.Context
import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.repository.ScheduleRepository
import com.medicapp.domain.DoseTaker
import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule
import com.medicapp.notifications.DoseAlarmScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class ScheduleDetailUiState(
    val schedule: MedicationSchedule? = null,
    val doses: List<MedicationDose> = emptyList(),
)

class ScheduleDetailViewModel(
    private val scheduleRepository: ScheduleRepository,
    private val doseTaker: DoseTaker,
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
        viewModelScope.launch {
            if (doseTaker.markTaken(schedule, dose)) {
                DoseAlarmScheduler.cancelDose(context.applicationContext, scheduleId, dose.id)
            }
        }
    }

    /**
     * Las alarmas se quitan de las tomas que el cierre pasó a omitidas, no de las que se veían en
     * pantalla: una reposición pudo crear otra justo antes. Sin conexión el cierre falla y la
     * pauta y sus alarmas quedan como estaban.
     */
    fun closeSchedule(context: Context) {
        val appContext = context.applicationContext
        viewModelScope.launch {
            val skipped = try {
                scheduleRepository.closeSchedule(scheduleId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("ScheduleDetailViewModel", "Could not close schedule", e)
                return@launch
            }
            skipped.forEach { DoseAlarmScheduler.cancelDose(appContext, scheduleId, it) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                ScheduleDetailViewModel(
                    app.container.scheduleRepository,
                    app.container.doseTaker,
                    createSavedStateHandle(),
                )
            }
        }
    }
}
