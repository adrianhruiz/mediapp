package com.medicapp.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.repository.ScheduleRepository
import com.medicapp.ui.minuteTicks
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.ZoneId

data class WeekHistoryUiState(
    val loading: Boolean = true,
    val plan: WeekPlan? = null,
    val offsetWeeks: Int = 0,
) {
    /** No se avanza más allá de la semana actual: las futuras solo tendrían tomas pendientes. */
    val canGoForward: Boolean get() = offsetWeeks < 0
}

class WeekHistoryViewModel(
    scheduleRepository: ScheduleRepository,
    private val zone: ZoneId = ZoneId.systemDefault(),
    clock: Flow<Long> = minuteTicks(),
) : ViewModel() {

    private val ticks = clock.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)
    private val offsetWeeks = MutableStateFlow(0)

    // El listener solo se rehace al cambiar de semana, no en cada minuto.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<WeekHistoryUiState> = combine(ticks, offsetWeeks) { now, offset ->
        WeekPlanner.weekStart(now, zone, offset) to offset
    }
        .distinctUntilChanged()
        .flatMapLatest { (start, offset) ->
            val (from, to) = WeekPlanner.range(start, zone)
            scheduleRepository.observeDosesBetween(from, to).map { doses -> Triple(start, offset, doses) }
        }
        .combine(ticks) { (start, offset, doses), now ->
            WeekHistoryUiState(
                loading = false,
                plan = WeekPlanner.plan(start, doses, now, zone),
                offsetWeeks = offset,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeekHistoryUiState())

    fun previousWeek() = offsetWeeks.update { it - 1 }

    fun nextWeek() = offsetWeeks.update { (it + 1).coerceAtMost(0) }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                WeekHistoryViewModel(app.container.scheduleRepository)
            }
        }
    }
}
