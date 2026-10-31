package com.medicapp.ui.schedule

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.domain.DoseScheduleCalculator
import com.medicapp.notifications.DoseAlarmScheduler
import com.medicapp.ui.components.SegmentedControl
import com.medicapp.ui.components.GelBarButton
import com.medicapp.ui.components.GroupHeader
import com.medicapp.ui.components.GroupedList
import com.medicapp.ui.components.GroupedRow
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.ScreenHeader
import com.medicapp.ui.theme.DmMono
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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
        ) {
            GelBarButton(onClick = { viewModel.save(context) }, enabled = uiState.canSave) {
                Text("Guardar")
            }
        }

        if (uiState.hasDuplicateActiveSchedule) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
            ) {
                Text(
                    "Ya tienes una pauta activa para este medicamento. " +
                        "Ciérrala antes de crear otra.",
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp),
                )
            }
        }

        GroupHeader("Frecuencia")
        SegmentedControl(
            options = intervalPresets.map { "$it h" } + "Otra",
            selectedIndex = if (uiState.isCustomInterval) {
                intervalPresets.size
            } else {
                intervalPresets.indexOf(uiState.intervalHoursInput.toIntOrNull()).takeIf { it >= 0 } ?: intervalPresets.size
            },
            onSelect = { index ->
                if (index < intervalPresets.size) {
                    viewModel.onIntervalPresetSelected(intervalPresets[index])
                } else {
                    viewModel.onCustomIntervalSelected()
                }
            },
        )
        if (uiState.isCustomInterval) {
            OutlinedTextField(
                value = uiState.intervalHoursInput,
                onValueChange = viewModel::onIntervalHoursChanged,
                label = { Text("Horas entre tomas") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                singleLine = true,
            )
        }

        GroupedList(modifier = Modifier.padding(top = 24.dp)) {
            GroupedRow {
                Text(
                    "Durante ${uiState.days} ${if (uiState.days == 1) "día" else "días"}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f),
                )
                GelBarButton(
                    onClick = viewModel::decrementDays,
                    enabled = uiState.days > 1,
                    modifier = Modifier.semantics { contentDescription = "Un día menos" },
                ) { Text("−") }
                GelBarButton(
                    onClick = viewModel::incrementDays,
                    enabled = uiState.days < ScheduleDuration.MAX_DAYS,
                    modifier = Modifier.semantics { contentDescription = "Un día más" },
                ) { Text("+") }
            }
            GroupedRow(divider = true, onClick = { showTimePicker = true }) {
                Text("Primera toma", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                val formattedStartAt = remember(uiState.startAtMillis) { previewHour.format(Date(uiState.startAtMillis)) }
                Text(
                    formattedStartAt,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = DmMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ScheduleSummary(uiState)

        GroupHeader("Motivo", modifier = Modifier.padding(top = 24.dp))
        if (uiState.reasons.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                uiState.reasons.forEach { reason ->
                    FilterChip(
                        selected = reason.nombre.equals(uiState.reasonInput.trim(), ignoreCase = true),
                        onClick = { viewModel.onReasonSelected(reason) },
                        label = { Text(reason.nombre) },
                    )
                }
            }
        }
        OutlinedTextField(
            value = uiState.reasonInput,
            onValueChange = viewModel::onReasonInputChanged,
            placeholder = { Text("Motivo (ej. alergia estacional)") },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            singleLine = true,
        )
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

/** Frecuencias de un toque; cualquier otra se escribe con «Otra». */
private val intervalPresets = listOf(8, 12, 24)

/** El total de tomas y cuándo acaba, en una línea: se ve sin hacer la cuenta de cabeza. */
@Composable
private fun ScheduleSummary(uiState: CreateScheduleUiState) {
    val interval = uiState.intervalHours ?: return
    val total = uiState.totalDoses ?: return
    val last = remember(uiState.startAtMillis, interval, total) {
        DoseScheduleCalculator.computeDoseTimes(uiState.startAtMillis, interval, total).last()
    }
    Text(
        ScheduleDuration.summary(total, last),
        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = DmMono),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 8.dp),
    )
}
