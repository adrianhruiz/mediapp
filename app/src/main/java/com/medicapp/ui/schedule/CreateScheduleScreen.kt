package com.medicapp.ui.schedule

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.domain.DoseInterval
import com.medicapp.domain.DoseScheduleCalculator
import com.medicapp.notifications.DoseAlarmScheduler
import com.medicapp.ui.components.GelBarButton
import com.medicapp.ui.components.GroupHeader
import com.medicapp.ui.components.GroupedList
import com.medicapp.ui.components.GroupedRow
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.ScreenHeader
import com.medicapp.ui.components.SegmentedControl
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
        // Guardar va en la cabecera; «Cancelar» sobra con la flecha atrás.
        ScreenHeader(
            title = MedicationNames.split(uiState.nombreMedicamento).first,
            eyebrow = if (uiState.isEditing) "Editar pauta" else "Nueva pauta",
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

        // Un solo control para la frecuencia. «Otra» abre cantidad + unidad (horas, días o
        // semanas) para lo que no está entre las tres de un toque.
        GroupHeader("Frecuencia")
        val isQuickInterval = !uiState.isCustomInterval && uiState.intervalHours in QUICK_INTERVAL_HOURS
        SegmentedControl(
            options = QUICK_INTERVAL_HOURS.map { "$it h" } + "Otra",
            selectedIndex = if (isQuickInterval) QUICK_INTERVAL_HOURS.indexOf(uiState.intervalHours) else QUICK_INTERVAL_HOURS.size,
            onSelect = { index ->
                if (index < QUICK_INTERVAL_HOURS.size) {
                    viewModel.onIntervalPresetSelected(QUICK_INTERVAL_HOURS[index])
                } else {
                    viewModel.onCustomIntervalSelected()
                }
            },
        )
        if (!isQuickInterval) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            ) {
                OutlinedTextField(
                    value = uiState.intervalAmountInput,
                    onValueChange = viewModel::onIntervalAmountChanged,
                    label = { Text("Cada") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                )
                listOf(
                    DoseInterval.Unit.HOURS to "horas",
                    DoseInterval.Unit.DAYS to "días",
                    DoseInterval.Unit.WEEKS to "semanas",
                ).forEach { (unit, label) ->
                    FilterChip(
                        selected = uiState.intervalUnit == unit,
                        onClick = { viewModel.onIntervalUnitChanged(unit) },
                        label = { Text(label) },
                    )
                }
            }
        }

        GroupedList(modifier = Modifier.padding(top = 24.dp)) {
            GroupedRow(chevron = false) {
                Text("Sin fin", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Switch(checked = uiState.openEnded, onCheckedChange = viewModel::onOpenEndedChanged)
            }
            // Una pauta sin fin no tiene duración: la fila de días sobra con el interruptor puesto.
            if (!uiState.openEnded) {
                GroupedRow(divider = true, chevron = false) {
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
            }
            GroupedRow(divider = true, onClick = { showTimePicker = true }) {
                Text(startTimeLabel(uiState), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                val formattedStartAt = remember(uiState.startAtMillis) { previewHour.format(Date(uiState.startAtMillis)) }
                Text(
                    formattedStartAt,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = DmMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ScheduleSummary(uiState)
        // Al editar, las tomas hechas se quedan: el total tiene que dejar alguna por hacer.
        if (uiState.keptDoseCount > 0 && !uiState.openEnded) {
            Text(
                "Ya llevas ${uiState.keptDoseCount}: el total tiene que ser mayor",
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.totalBelowTaken) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp),
            )
        }

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

        if (uiState.saveFailed) {
            Text(
                "No se han podido guardar los cambios. Editar una pauta necesita conexión.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
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
            title = { Text("Hora de la ${startTimeLabel(uiState).lowercase()}") },
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

/** Al editar, la hora que se pide es la de la próxima toma, no la de la primera. */
private fun startTimeLabel(uiState: CreateScheduleUiState) =
    if (uiState.isEditing) "Próxima toma" else "Primera toma"

private val previewHour = SimpleDateFormat("HH:mm", Locale.forLanguageTag("es-ES"))

/**
 * El total de tomas y cuándo acaba, en una línea: se ve sin hacer la cuenta de cabeza. Al
 * editar solo cuentan las que faltan; las hechas se quedan como están.
 */
@Composable
private fun ScheduleSummary(uiState: CreateScheduleUiState) {
    val interval = uiState.intervalHours ?: return
    if (uiState.openEnded) {
        Text(
            "Se repite hasta que la cierres",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp, top = 8.dp),
        )
        return
    }
    val total = uiState.totalDoses?.minus(uiState.keptDoseCount)?.takeIf { it > 0 } ?: return
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
