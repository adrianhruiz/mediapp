package com.medicapp.ui.schedule

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.domain.DoseScheduleCalculator
import com.medicapp.model.Reason
import com.medicapp.notifications.DoseAlarmScheduler
import com.medicapp.ui.components.BlisterCell
import com.medicapp.ui.components.DoseCellState
import com.medicapp.ui.components.Eyebrow
import com.medicapp.ui.components.FoilCard
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.PillStyles
import com.medicapp.ui.components.ScreenHeader
import com.medicapp.ui.components.SecondaryButton
import com.medicapp.ui.theme.DmMono
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateScheduleScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: CreateScheduleViewModel = viewModel(factory = CreateScheduleViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onDone()
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { /* no-op: si se deniega, las notificaciones simplemente no se mostrarán */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = MedicationNames.split(uiState.nombreMedicamento).first,
            eyebrow = "Nueva pauta",
            onBack = onCancel,
            modifier = Modifier.padding(bottom = 16.dp),
        )

        if (uiState.hasDuplicateActiveSchedule) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text(
                    "Ya tienes una pauta activa para este medicamento. " +
                        "Ciérrala antes de crear otra.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        Text("Frecuencia", style = MaterialTheme.typography.titleMedium)
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 8.dp),
        ) {
            listOf(8 to "Cada 8h", 12 to "Cada 12h", 24 to "Cada 24h").forEach { (hours, label) ->
                FilterChip(
                    selected = uiState.intervalHoursInput == hours.toString(),
                    onClick = { viewModel.onIntervalPresetSelected(hours) },
                    label = { Text(label) },
                )
            }
        }
        OutlinedTextField(
            value = uiState.intervalHoursInput,
            onValueChange = viewModel::onIntervalHoursChanged,
            label = { Text("Horas entre tomas") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
        )

        OutlinedTextField(
            value = uiState.totalDosesInput,
            onValueChange = viewModel::onTotalDosesChanged,
            label = { Text("Cantidad de tomas del periodo") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            singleLine = true,
        )

        Text(
            "Hora de la primera toma",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 24.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            val timeFormatter = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
            val formattedStartAt = remember(uiState.startAtMillis) {
                timeFormatter.format(Date(uiState.startAtMillis))
            }
            Text(
                formattedStartAt,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(end = 12.dp),
            )
            SecondaryButton(onClick = { showTimePicker = true }) {
                Text("Cambiar hora")
            }
        }

        FirstDayPreview(uiState)

        OutlinedTextField(
            value = uiState.reasonInput,
            onValueChange = viewModel::onReasonInputChanged,
            label = { Text("Motivo (ej. alergia estacional)") },
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
            singleLine = true,
        )
        if (uiState.suggestions.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                uiState.suggestions.forEach { reason ->
                    ReasonSuggestionRow(reason = reason, onClick = { viewModel.onSuggestionSelected(reason) })
                }
            }
        }

        Button(
            onClick = { viewModel.save(context) },
            enabled = uiState.reasonInput.isNotBlank() &&
                uiState.intervalHoursInput.isNotBlank() &&
                uiState.totalDosesInput.isNotBlank() &&
                !uiState.hasDuplicateActiveSchedule,
            modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
        ) {
            Text("Guardar pauta")
        }
        SecondaryButton(onClick = onCancel, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Text("Cancelar")
        }
    }

    if (showTimePicker) {
        val calendar = remember { Calendar.getInstance().apply { timeInMillis = uiState.startAtMillis } }
        val timePickerState = rememberTimePickerState(
            initialHour = calendar.get(Calendar.HOUR_OF_DAY),
            initialMinute = calendar.get(Calendar.MINUTE),
            is24Hour = true,
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text("Hora de la primera toma") },
            text = { TimePicker(state = timePickerState) },
            confirmButton = {
                TextButton(onClick = {
                    val newCalendar = Calendar.getInstance().apply {
                        timeInMillis = uiState.startAtMillis
                        set(Calendar.HOUR_OF_DAY, timePickerState.hour)
                        set(Calendar.MINUTE, timePickerState.minute)
                        set(Calendar.SECOND, 0)
                    }
                    viewModel.onStartAtChanged(newCalendar.timeInMillis)
                    showTimePicker = false
                }) { Text("Aceptar") }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) { Text("Cancelar") }
            },
        )
    }

    if (uiState.needsExactAlarmPermission) {
        AlertDialog(
            onDismissRequest = viewModel::dismissExactAlarmPrompt,
            title = { Text("Permiso necesario") },
            text = {
                Text(
                    "Para avisarte a la hora exacta de cada toma, Mediapp necesita el permiso " +
                        "\"Alarmas y recordatorios\". Actívalo en Ajustes y vuelve a guardar.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.dismissExactAlarmPrompt()
                    DoseAlarmScheduler.requestExactAlarmPermission(context)
                }) { Text("Ir a Ajustes") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissExactAlarmPrompt) { Text("Cancelar") }
            },
        )
    }
}

private val previewHour = SimpleDateFormat("HH:mm", Locale.forLanguageTag("es-ES"))
private val previewDay = SimpleDateFormat("EEEE d MMM 'a las' HH:mm", Locale.forLanguageTag("es-ES"))

/** Más celdas no caben en una fila y no aportan: con ver el ritmo del primer día basta. */
private const val MAX_PREVIEW_CELLS = 6

/**
 * Las tomas del primer día en celdas de blíster, y cuándo acaba la pauta: se ve el ritmo antes
 * de guardar, sin tener que hacer la cuenta de cabeza.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FirstDayPreview(uiState: CreateScheduleUiState) {
    val interval = uiState.intervalHoursInput.toIntOrNull()?.takeIf { it > 0 } ?: return
    val total = uiState.totalDosesInput.toIntOrNull()?.takeIf { it > 0 } ?: return
    val times = DoseScheduleCalculator.computeDoseTimes(uiState.startAtMillis, interval, total)
    val firstDay = times.takeWhile { it < uiState.startAtMillis + 24 * 3_600_000L }.take(MAX_PREVIEW_CELLS)
    val pill = PillStyles.forMedication(uiState.nregistro, uiState.nombreMedicamento)

    Eyebrow("Primer día", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
    FoilCard(modifier = Modifier.fillMaxWidth()) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            firstDay.forEach { time ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BlisterCell(state = DoseCellState.PENDING, pill = pill, width = 44.dp)
                    Text(
                        previewHour.format(Date(time)),
                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = DmMono),
                    )
                }
            }
        }
    }
    Text(
        "$total ${if (total == 1) "toma" else "tomas"} · la última el ${previewDay.format(Date(times.last())).replace(".", "")}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ReasonSuggestionRow(reason: Reason, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(onClick = onClick),
    ) {
        Text(reason.nombre, modifier = Modifier.padding(12.dp))
    }
}
