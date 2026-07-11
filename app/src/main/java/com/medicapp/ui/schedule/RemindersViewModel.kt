package com.medicapp.ui.schedule

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.repository.PagedResult
import com.medicapp.data.repository.PagingDefaults
import com.medicapp.data.repository.ScheduleRepository
import com.medicapp.model.MedicationSchedule
import com.medicapp.notifications.DoseAlarmScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class RemindersViewModel(
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {

    private val _limit = MutableStateFlow(PagingDefaults.PAGE_SIZE)

    @OptIn(ExperimentalCoroutinesApi::class)
    private val schedulesPage: StateFlow<PagedResult<MedicationSchedule>> = _limit
        .flatMapLatest { limit -> scheduleRepository.getSchedulesPage(limit) }
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            PagedResult(items = emptyList(), hasMore = true, isFromCache = false),
        )

    val schedules: StateFlow<List<MedicationSchedule>> = schedulesPage
        .map { it.items }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val hasMore: StateFlow<Boolean> = schedulesPage
        .map { it.hasMore }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun loadMore() {
        if (schedulesPage.value.hasMore) {
            _limit.update { it + PagingDefaults.PAGE_INCREMENT }
        }
    }

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
