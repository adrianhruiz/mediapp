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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
                title = { Text(schedule?.nombreMedicamento ?: "Recordatorio") },
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
                Text(
                    "Cada ${schedule.intervalHours}h · $takenCount/${schedule.totalDoses} tomas · " +
                        schedule.reasonNombre,
                    style = MaterialTheme.typography.bodyLarge,
                )
                if (schedule.status == ScheduleStatus.ACTIVE) {
                    OutlinedButton(
                        onClick = { viewModel.closeSchedule(context) },
                        modifier = Modifier.padding(top = 12.dp),
                    ) { Text("Cerrar periodo") }
                }
            }

            Text(
                "Tomas",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
            )
            uiState.doses.forEach { dose ->
                DoseRow(dose = dose, onMarkTaken = { viewModel.markDoseTaken(context, dose) })
            }
        }
    }
}

@Composable
private fun DoseRow(dose: MedicationDose, onMarkTaken: () -> Unit) {
    val formatter = remember { SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()) }
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text("Toma ${dose.doseNumber}", style = MaterialTheme.typography.bodyLarge)
                Text(formatter.format(Date(dose.scheduledAtMillis)), style = MaterialTheme.typography.bodySmall)
            }
            when (dose.status) {
                DoseStatus.PENDING -> TextButton(onClick = onMarkTaken) { Text("Marcar tomada") }
                DoseStatus.TAKEN -> Icon(Icons.Default.CheckCircle, contentDescription = "Tomada")
                DoseStatus.SKIPPED -> Text("Omitida", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
