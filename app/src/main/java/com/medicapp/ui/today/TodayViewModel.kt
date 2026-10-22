package com.medicapp.ui.today

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.medicapp.MedicApp
import com.medicapp.data.repository.ScheduleRepository
import com.medicapp.domain.DoseTaker
import com.medicapp.notifications.DoseAlarmScheduler
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId

data class TodayUiState(
    val loading: Boolean = true,
    val nowMillis: Long = 0L,
    val plan: TodayPlan = TodayPlan(),
)

/** Inicio y fin (excluido) del día natural que contiene [nowMillis]. */
internal fun dayRange(nowMillis: Long, zone: ZoneId): Pair<Long, Long> {
    val day = Instant.ofEpochMilli(nowMillis).atZone(zone).toLocalDate()
    val from = day.atStartOfDay(zone).toInstant().toEpochMilli()
    val to = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
    return from to to
}

/** La hora actual, una vez por minuto: basta para "toca en 5 min" y para cambiar de día. */
private fun minuteTicks(): Flow<Long> = flow {
    while (true) {
        val now = System.currentTimeMillis()
        emit(now)
        delay(60_000 - now % 60_000)
    }
}

class TodayViewModel(
    scheduleRepository: ScheduleRepository,
    private val doseTaker: DoseTaker,
    zone: ZoneId = ZoneId.systemDefault(),
    clock: Flow<Long> = minuteTicks(),
) : ViewModel() {

    private val ticks = clock.shareIn(viewModelScope, SharingStarted.WhileSubscribed(), replay = 1)

    // El listener de las tomas solo se rehace al cambiar de día, no en cada minuto.
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<TodayUiState> = ticks
        .map { now -> dayRange(now, zone) }
        .distinctUntilChanged()
        .flatMapLatest { (from, to) -> scheduleRepository.observeDosesBetween(from, to) }
        .combine(ticks) { doses, now ->
            TodayUiState(loading = false, nowMillis = now, plan = TodayPlanner.plan(doses, now))
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), TodayUiState())

    fun markTaken(context: Context, dose: TodayDose) {
        viewModelScope.launch {
            if (doseTaker.markTaken(dose.schedule, dose.dose)) {
                DoseAlarmScheduler.cancelDose(context.applicationContext, dose.schedule.id, dose.dose.id)
            }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] as MedicApp
                TodayViewModel(app.container.scheduleRepository, app.container.doseTaker)
            }
        }
    }
}
