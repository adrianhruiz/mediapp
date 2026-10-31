package com.medicapp.ui.schedule

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.MedicationSchedule
import com.medicapp.model.ScheduleStatus
import com.medicapp.ui.components.BlisterCell
import com.medicapp.ui.components.DoseCellState
import com.medicapp.ui.components.Eyebrow
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.OnBottomReached
import com.medicapp.ui.components.PillStyles
import com.medicapp.ui.components.ScreenHeader
import com.medicapp.ui.components.SecondaryButton
import com.medicapp.ui.theme.DmMono

@Composable
fun RemindersScreen(
    contentPadding: PaddingValues,
    onOpenSchedule: (MedicationSchedule) -> Unit,
    onBack: () -> Unit,
    viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory),
) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val active = schedules.filter { it.status == ScheduleStatus.ACTIVE }
    val closed = schedules.filter { it.status == ScheduleStatus.CLOSED }
    val listState = rememberLazyListState()
    listState.OnBottomReached(onLoadMore = viewModel::loadMore)

    val header = @Composable {
        ScreenHeader(title = "Pautas", eyebrow = "Recordatorios de toma", onBack = onBack)
    }

    if (schedules.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding).padding(horizontal = 16.dp, vertical = 8.dp)) {
            header()
            Text(
                "Todavía no tienes pautas. Crea una desde la ficha de un medicamento.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(vertical = 24.dp),
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(contentPadding),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { header() }
        if (active.isNotEmpty()) {
            item { Eyebrow("Activas", modifier = Modifier.padding(top = 8.dp)) }
            items(active, key = { it.id }) { schedule ->
                ScheduleCard(
                    schedule = schedule,
                    onClick = { onOpenSchedule(schedule) },
                    onClose = { viewModel.closeSchedule(context, schedule) },
                )
            }
        }
        if (closed.isNotEmpty()) {
            item { Eyebrow("Cerradas", modifier = Modifier.padding(top = 16.dp)) }
            items(closed, key = { it.id }) { schedule ->
                ScheduleCard(schedule = schedule, onClick = { onOpenSchedule(schedule) }, onClose = null)
            }
        }
        if (hasMore) {
            item {
                Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun ScheduleCard(schedule: MedicationSchedule, onClick: () -> Unit, onClose: (() -> Unit)?) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            BlisterCell(
                state = if (schedule.status == ScheduleStatus.ACTIVE) DoseCellState.PENDING else DoseCellState.TAKEN,
                pill = PillStyles.forMedication(schedule.nregistro, schedule.nombreMedicamento),
                width = 30.dp,
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(MedicationNames.split(schedule.nombreMedicamento).first, style = MaterialTheme.typography.titleMedium)
                Text(
                    "Cada ${schedule.intervalHours} h · ${schedule.totalDoses} tomas",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = DmMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "Para: ${schedule.reasonNombre}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (onClose != null) {
                    SecondaryButton(onClick = onClose, modifier = Modifier.padding(top = 8.dp)) { Text("Cerrar periodo") }
                }
            }
        }
    }
}
