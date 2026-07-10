package com.medicapp.ui.schedule

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.Reason
import com.medicapp.notifications.DoseAlarmScheduler
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
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Crear recordatorio", style = MaterialTheme.typography.titleLarge)
        Text(
            uiState.nombreMedicamento,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
        )

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
            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            modifier = Modifier.padding(top = 8.dp),
        ) {
            Text(
                SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(uiState.startAtMillis)),
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier.padding(end = 12.dp),
            )
            OutlinedButton(onClick = { showTimePicker = true }) {
                Text("Cambiar hora")
            }
        }

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

        Row(modifier = Modifier.padding(top = 24.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) {
                Text("Cancelar")
            }
            Button(
                onClick = { viewModel.save(context) },
                enabled = uiState.reasonInput.isNotBlank() &&
                    uiState.intervalHoursInput.isNotBlank() &&
                    uiState.totalDosesInput.isNotBlank(),
            ) {
                Text("Guardar")
            }
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
