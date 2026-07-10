package com.medicapp.ui.history

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.Reason
import com.medicapp.model.TakenEntry
import com.medicapp.ui.components.MedicationThumbnail
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    contentPadding: PaddingValues,
    onEditEntry: (TakenEntry) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val reasons by viewModel.reasons.collectAsStateWithLifecycle()
    val selectedReasonId by viewModel.selectedReasonId.collectAsStateWithLifecycle()
    val hasAnyEntries by viewModel.hasAnyEntries.collectAsStateWithLifecycle()
    var entryPendingDelete by remember { mutableStateOf<TakenEntry?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
    ) {
        if (hasAnyEntries) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Buscar por medicamento o motivo") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
            ReasonFilterDropdown(
                reasons = reasons,
                selectedReasonId = selectedReasonId,
                onReasonSelected = viewModel::onReasonFilterChanged,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        }

        if (!hasAnyEntries) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Todavía no has registrado ningún medicamento tomado",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else if (entries.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No hay medicamentos que coincidan con \"$query\"",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(24.dp),
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(entries, key = { it.id }) { entry ->
                    HistoryEntryCard(
                        entry = entry,
                        onEdit = { onEditEntry(entry) },
                        onDelete = { entryPendingDelete = entry },
                    )
                }
            }
        }
    }

    entryPendingDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryPendingDelete = null },
            title = { Text("Eliminar entrada") },
            text = { Text("¿Seguro que quieres eliminar \"${entry.nombreMedicamento}\" del historial?") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(entry)
                    entryPendingDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { entryPendingDelete = null }) { Text("Cancelar") }
            },
        )
    }
}

private val formatter = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")

@Composable
private fun HistoryEntryCard(entry: TakenEntry, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MedicationThumbnail(fotoUrl = entry.fotoUrl, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(entry.nombreMedicamento, style = MaterialTheme.typography.titleMedium)
                Text(entry.reasonNombre, style = MaterialTheme.typography.bodyMedium)
                val formattedDate = Instant.ofEpochMilli(entry.timestampMillis)
                    .atZone(ZoneId.systemDefault())
                    .format(formatter)
                Text(formattedDate, style = MaterialTheme.typography.bodySmall)
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Editar")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Eliminar")
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ReasonFilterDropdown(
    reasons: List<Reason>,
    selectedReasonId: String?,
    onReasonSelected: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = reasons.firstOrNull { it.id == selectedReasonId }?.nombre ?: "Todos los motivos"

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }, modifier = modifier) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            label = { Text("Filtrar por motivo") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Todos los motivos") },
                onClick = {
                    onReasonSelected(null)
                    expanded = false
                },
            )
            reasons.forEach { reason ->
                DropdownMenuItem(
                    text = { Text(reason.nombre) },
                    onClick = {
                        onReasonSelected(reason.id)
                        expanded = false
                    },
                )
            }
        }
    }
}
