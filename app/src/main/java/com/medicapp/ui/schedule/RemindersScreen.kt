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
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.MedicationSchedule
import com.medicapp.model.ScheduleStatus
import com.medicapp.ui.components.OnBottomReached

@Composable
fun RemindersScreen(
    contentPadding: PaddingValues,
    onOpenSchedule: (MedicationSchedule) -> Unit,
    viewModel: RemindersViewModel = viewModel(factory = RemindersViewModel.Factory),
) {
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val active = schedules.filter { it.status == ScheduleStatus.ACTIVE }
    val closed = schedules.filter { it.status == ScheduleStatus.CLOSED }
    val listState = rememberLazyListState()
    listState.OnBottomReached(onLoadMore = viewModel::loadMore)

    if (schedules.isEmpty()) {
        Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
            Text(
                "Todavía no tienes recordatorios. Crea uno desde el detalle de un medicamento.",
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(24.dp),
            )
        }
        return
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize().padding(contentPadding).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (active.isNotEmpty()) {
            item { Text("Activos", style = MaterialTheme.typography.titleLarge) }
            items(active, key = { it.id }) { schedule ->
                ScheduleCard(
                    schedule = schedule,
                    onClick = { onOpenSchedule(schedule) },
                    onClose = { viewModel.closeSchedule(context, schedule) },
                )
            }
        }
        if (closed.isNotEmpty()) {
            item {
                Text(
                    "Cerrados",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
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
        Column(modifier = Modifier.padding(16.dp)) {
            Text(schedule.nombreMedicamento, style = MaterialTheme.typography.titleMedium)
            Text(
                "Cada ${schedule.intervalHours}h · ${schedule.totalDoses} tomas · ${schedule.reasonNombre}",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 4.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AssistChip(
                    onClick = {},
                    label = { Text(if (schedule.status == ScheduleStatus.ACTIVE) "Activo" else "Cerrado") },
                )
                if (onClose != null) {
                    OutlinedButton(onClick = onClose) { Text("Cerrar periodo") }
                }
            }
        }
    }
}
