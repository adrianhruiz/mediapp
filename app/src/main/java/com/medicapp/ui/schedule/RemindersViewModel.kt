package com.medicapp.ui.schedule

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.repository.ScheduleRepository
import com.medicapp.model.MedicationSchedule
import com.medicapp.notifications.DoseAlarmScheduler
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class RemindersViewModel(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    val schedules: StateFlow<List<MedicationSchedule>> = scheduleRepository.getSchedules()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun closeSchedule(context: Context, schedule: MedicationSchedule) {
        viewModelScope.launch {
            val pendingDoses = scheduleRepository.getPendingDoses(schedule.id)
            pendingDoses.forEach { dose ->
                DoseAlarmScheduler.cancelDose(context.applicationContext, schedule.id, dose.id)
            }
            scheduleRepository.closeSchedule(schedule.id)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                RemindersViewModel(app.container.scheduleRepository)
            }
        }
    }
}
