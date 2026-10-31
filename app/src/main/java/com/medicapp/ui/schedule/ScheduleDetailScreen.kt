package com.medicapp.ui.schedule

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose
import com.medicapp.model.ScheduleStatus
import com.medicapp.ui.components.BlisterCell
import com.medicapp.ui.components.DoseCellState
import com.medicapp.ui.components.Eyebrow
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.PillStyle
import com.medicapp.ui.components.PillStyles
import com.medicapp.ui.components.SecondaryButton
import com.medicapp.ui.theme.DmMono
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleDetailScreen(
    onBack: () -> Unit,
    viewModel: ScheduleDetailViewModel = viewModel(factory = ScheduleDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val schedule = uiState.schedule

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Eyebrow("Pauta") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            if (schedule != null) {
                val takenCount = uiState.doses.count { it.status == DoseStatus.TAKEN }
                Text(MedicationNames.split(schedule.nombreMedicamento).first, style = MaterialTheme.typography.headlineSmall)
                Text(
                    "Cada ${schedule.intervalHours} h · $takenCount/${schedule.totalDoses} tomas",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = DmMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                Text(
                    "Para: ${schedule.reasonNombre}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (schedule.status == ScheduleStatus.ACTIVE) {
                    SecondaryButton(
                        onClick = { viewModel.closeSchedule(context) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Cerrar periodo") }
                }
            }

            Eyebrow("Tomas", modifier = Modifier.padding(top = 24.dp, bottom = 8.dp))
            val pill = schedule?.let { PillStyles.forMedication(it.nregistro, it.nombreMedicamento) }
            uiState.doses.forEach { dose ->
                DoseRow(dose = dose, pill = pill, onMarkTaken = { viewModel.markDoseTaken(context, dose) })
            }
        }
    }
}

@Composable
private fun DoseRow(dose: MedicationDose, pill: PillStyle?, onMarkTaken: () -> Unit) {
    val formatter = remember { SimpleDateFormat("EEE d MMM · HH:mm", Locale.forLanguageTag("es-ES")) }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (pill != null) {
                BlisterCell(
                    state = when (dose.status) {
                        DoseStatus.PENDING -> DoseCellState.PENDING
                        DoseStatus.TAKEN -> DoseCellState.TAKEN
                        DoseStatus.SKIPPED -> DoseCellState.SKIPPED
                    },
                    pill = pill,
                    width = 28.dp,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("Toma ${dose.doseNumber}", style = MaterialTheme.typography.bodyLarge)
                Text(
                    formatter.format(Date(dose.scheduledAtMillis)).replace(".", ""),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = DmMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            when (dose.status) {
                DoseStatus.PENDING -> TextButton(onClick = onMarkTaken) { Text("Marcar tomada") }
                DoseStatus.TAKEN -> Text("Tomada", style = MaterialTheme.typography.labelMedium)
                DoseStatus.SKIPPED -> Text("Omitida", style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
