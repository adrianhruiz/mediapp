package com.medicapp.ui.history

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.DoseStatus
import com.medicapp.ui.components.BlisterCell
import com.medicapp.ui.components.DoseCellState
import com.medicapp.ui.components.FoilCard
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.PillStyles
import com.medicapp.ui.theme.DmMono
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish = Locale.forLanguageTag("es-ES")
private val dayOfMonth = DateTimeFormatter.ofPattern("d", spanish)
private val dayAndMonth = DateTimeFormatter.ofPattern("d MMM", spanish)
private val dayLetters = listOf("L", "M", "X", "J", "V", "S", "D")

private fun weekTitle(start: LocalDate, end: LocalDate): String {
    val first = if (start.month == end.month) dayOfMonth.format(start) else dayAndMonth.format(start)
    return "Semana del $first al ${dayAndMonth.format(end)}".replace(".", "")
}

private fun WeekDose.description(): String {
    val time = Instant.ofEpochMilli(dose.scheduledAtMillis).atZone(ZoneId.systemDefault())
    val name = MedicationNames.split(schedule.nombreMedicamento).first
    val status = when {
        dose.status == DoseStatus.TAKEN -> "tomada"
        dose.status == DoseStatus.SKIPPED -> "saltada"
        missed -> "sin tomar"
        else -> "pendiente"
    }
    return "${DateTimeFormatter.ofPattern("HH:mm").format(time)}, $name, $status"
}

/** La semana: primero el resumen, luego una tira de 7 días con una celda por toma. */
@Composable
fun WeekHistoryContent(
    modifier: Modifier = Modifier,
    viewModel: WeekHistoryViewModel = viewModel(factory = WeekHistoryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val plan = state.plan

    if (state.loading || plan == null) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = viewModel::previousWeek) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Semana anterior")
            }
            Text(
                weekTitle(plan.start, plan.end),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = viewModel::nextWeek, enabled = state.canGoForward) {
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Semana siguiente")
            }
        }

        Column(modifier = Modifier.padding(horizontal = 4.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                WeekSummary.takenLabel(plan.taken, plan.due),
                style = MaterialTheme.typography.headlineSmall,
            )
            // Lo único en coral de la pantalla: la cifra de tomas que se escaparon.
            WeekSummary.missedLabel(plan.missed.size)?.let {
                Text(it, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
            }
        }

        FoilCard(modifier = Modifier.fillMaxWidth()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                plan.days.forEachIndexed { index, day ->
                    DayColumn(letter = dayLetters[index], day = day, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** Un día de la tira: letra, número y sus tomas apiladas; un guion si ese día no había ninguna. */
@Composable
private fun DayColumn(letter: String, day: WeekDay, modifier: Modifier = Modifier) {
    val ink = if (day.isToday) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
    val weight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(letter, style = MaterialTheme.typography.labelLarge.copy(fontFamily = DmMono), fontWeight = weight, color = ink)
        Text(
            dayOfMonth.format(day.date),
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = DmMono),
            fontWeight = weight,
            color = ink,
        )
        if (day.doses.isEmpty()) {
            Text(
                "–",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(vertical = 8.dp)
                    .semantics { contentDescription = "Sin tomas" },
            )
        } else {
            day.doses.forEach { weekDose ->
                BlisterCell(
                    state = when (weekDose.dose.status) {
                        DoseStatus.PENDING -> DoseCellState.PENDING
                        DoseStatus.TAKEN -> DoseCellState.TAKEN
                        DoseStatus.SKIPPED -> DoseCellState.SKIPPED
                    },
                    pill = PillStyles.forMedication(weekDose.schedule.nregistro, weekDose.schedule.nombreMedicamento),
                    width = 20.dp,
                    modifier = Modifier.semantics { contentDescription = weekDose.description() },
                )
            }
        }
    }
}
