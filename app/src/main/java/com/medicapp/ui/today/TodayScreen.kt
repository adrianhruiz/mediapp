package com.medicapp.ui.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EventNote
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.ui.components.GelButton
import com.medicapp.ui.components.GelTone
import com.medicapp.model.DoseStatus
import com.medicapp.ui.backup.BackupMenu
import com.medicapp.ui.components.BlisterCell
import com.medicapp.ui.components.DoseCellState
import com.medicapp.ui.components.Eyebrow
import com.medicapp.ui.components.FoilCard
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.PillStyles
import com.medicapp.ui.theme.DmMono
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val spanish = Locale.forLanguageTag("es-ES")
private val hourFormat = DateTimeFormatter.ofPattern("HH:mm", spanish)
private val dayFormat = DateTimeFormatter.ofPattern("EEEE · d MMM", spanish)

private fun Long.hour(): String = hourFormat.format(Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()))

private fun dayLabel(nowMillis: Long): String =
    dayFormat.format(Instant.ofEpochMilli(nowMillis).atZone(ZoneId.systemDefault())).replace(".", "").uppercase(spanish)

private fun TodayDose.cellState(): DoseCellState = when (dose.status) {
    DoseStatus.PENDING -> DoseCellState.PENDING
    DoseStatus.TAKEN -> DoseCellState.TAKEN
    DoseStatus.SKIPPED -> DoseCellState.SKIPPED
}

private fun TodayDose.statusText(): String = when {
    dose.status == DoseStatus.TAKEN -> "Tomada"
    dose.status == DoseStatus.SKIPPED -> "Saltada"
    overdue -> "Sin tomar"
    else -> "Pendiente"
}

/** La frase de cabecera de la tarjeta: cuándo toca, o qué pasó con esta toma. */
private fun TodayDose.whenText(nowMillis: Long): String {
    val at = dose.scheduledAtMillis
    return when (dose.status) {
        DoseStatus.TAKEN -> dose.takenAtMillis?.let { "Tomada a las ${it.hour()}" } ?: "Tomada"
        DoseStatus.SKIPPED -> "Saltada al cerrar la pauta"
        DoseStatus.PENDING -> {
            val minutes = (at - nowMillis) / 60_000
            when {
                overdue -> "Te toca desde las ${at.hour()}"
                minutes < 1 -> "Te toca ahora"
                minutes < 60 -> "Toca en $minutes min"
                else -> "Toca a las ${at.hour()}"
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    onOpenReminders: () -> Unit,
    onSearch: () -> Unit,
    onLogout: () -> Unit,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    val plan = state.plan
    // Sin elección del usuario se enseña la que toca; con todo hecho, la última del día.
    val selected = plan.doses.firstOrNull { it.dose.id == selectedId } ?: plan.next ?: plan.doses.lastOrNull()

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                TodayHeader(
                    nowMillis = state.nowMillis,
                    doseCount = plan.doses.size,
                    loading = state.loading,
                    onOpenReminders = onOpenReminders,
                    snackbarHostState = snackbarHostState,
                    onLogout = onLogout,
                )
            }
            when {
                state.loading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                plan.doses.isEmpty() -> item { EmptyToday(onSearch = onSearch, onOpenReminders = onOpenReminders) }
                else -> {
                    item {
                        FoilCard(modifier = Modifier.fillMaxWidth()) {
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly,
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                plan.doses.forEach { dose ->
                                    DoseCell(
                                        dose = dose,
                                        attention = dose == plan.next || dose.overdue,
                                        selected = dose == selected,
                                        onClick = { selectedId = dose.dose.id },
                                    )
                                }
                            }
                        }
                    }
                    if (plan.allDone) {
                        item {
                            Text(
                                "Ya no te queda ninguna toma hoy.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    selected?.let { dose ->
                        item {
                            DoseCard(
                                dose = dose,
                                nowMillis = state.nowMillis,
                                onMarkTaken = { viewModel.markTaken(context, dose) },
                            )
                        }
                    }
                    item { Eyebrow("Todas las tomas de hoy") }
                    items(plan.doses, key = { it.dose.id }) { dose ->
                        DoseRow(dose = dose, selected = dose == selected, onClick = { selectedId = dose.dose.id })
                    }
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TodayHeader(
    nowMillis: Long,
    doseCount: Int,
    loading: Boolean,
    onOpenReminders: () -> Unit,
    snackbarHostState: SnackbarHostState,
    onLogout: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (nowMillis > 0) Eyebrow(dayLabel(nowMillis))
            Text(
                text = when {
                    loading -> "Hoy"
                    doseCount == 0 -> "Hoy, sin tomas"
                    doseCount == 1 -> "Hoy, 1 toma"
                    else -> "Hoy, $doseCount tomas"
                },
                style = MaterialTheme.typography.headlineMedium,
            )
        }
        IconButton(onClick = onOpenReminders) {
            Icon(Icons.Default.EventNote, contentDescription = "Pautas y recordatorios")
        }
        BackupMenu(snackbarHostState = snackbarHostState, onLogout = onLogout)
    }
}

@Composable
private fun DoseCell(dose: TodayDose, attention: Boolean, selected: Boolean, onClick: () -> Unit) {
    val (name, _) = MedicationNames.split(dose.schedule.nombreMedicamento)
    val time = dose.dose.scheduledAtMillis.hour()
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = "$time, $name, ${dose.statusText().lowercase(spanish)}"
                this.selected = selected
            }
            .padding(4.dp),
    ) {
        BlisterCell(
            state = dose.cellState(),
            pill = PillStyles.forMedication(dose.schedule.nregistro, dose.schedule.nombreMedicamento),
            width = 52.dp,
            highlighted = attention,
        )
        Text(
            text = time,
            style = MaterialTheme.typography.labelLarge.copy(fontFamily = DmMono),
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DoseCard(dose: TodayDose, nowMillis: Long, onMarkTaken: () -> Unit) {
    val (name, detail) = MedicationNames.split(dose.schedule.nombreMedicamento)
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            // En rojo solo lo que urge: la atrasada o la que toca dentro de una hora. Una toma de
            // dentro de cinco horas en rojo alarmaba sin motivo.
            val urgent = dose.overdue || (dose.pending && dose.dose.scheduledAtMillis - nowMillis < 3_600_000)
            Eyebrow(
                text = dose.whenText(nowMillis),
                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(name, style = MaterialTheme.typography.headlineSmall)
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = DmMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(
                "Para: ${dose.schedule.reasonNombre}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dose.pending) {
                GelButton(onClick = onMarkTaken, tone = GelTone.CORAL, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Marcar como tomada")
                }
            }
        }
    }
}

@Composable
private fun DoseRow(dose: TodayDose, selected: Boolean, onClick: () -> Unit) {
    val (name, _) = MedicationNames.split(dose.schedule.nombreMedicamento)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .semantics { this.selected = selected }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            dose.dose.scheduledAtMillis.hour(),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = DmMono),
            modifier = Modifier.width(48.dp),
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            dose.statusText(),
            style = MaterialTheme.typography.labelMedium,
            color = if (dose.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun EmptyToday(onSearch: () -> Unit, onOpenReminders: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Hoy no tienes tomas", style = MaterialTheme.typography.titleLarge)
            Text(
                "Las tomas salen de tus pautas. Busca un medicamento y crea una pauta desde su ficha.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            GelButton(onClick = onSearch, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text("Buscar medicamento")
            }
            TextButton(onClick = onOpenReminders, modifier = Modifier.fillMaxWidth()) {
                Text("Ver mis pautas")
            }
        }
    }
}
