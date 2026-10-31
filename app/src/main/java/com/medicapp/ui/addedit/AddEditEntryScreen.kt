package com.medicapp.ui.addedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.ui.components.GelButton
import com.medicapp.model.Reason
import com.medicapp.ui.components.AddedChip
import com.medicapp.ui.components.GroupedList
import com.medicapp.ui.components.GroupedRow
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.ScreenHeader
import com.medicapp.ui.components.SecondaryButton

@Composable
fun AddEditEntryScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: AddEditEntryViewModel = viewModel(factory = AddEditEntryViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSaved, uiState.isDeleted) {
        if (uiState.isSaved || uiState.isDeleted) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars)
            .padding(16.dp),
    ) {
        ScreenHeader(
            title = MedicationNames.split(uiState.nombreMedicamento).first,
            eyebrow = if (uiState.isEditMode) "Editar entrada" else "Añadir al historial",
            onBack = onCancel,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        OutlinedTextField(
            value = uiState.reasonInput,
            onValueChange = viewModel::onReasonInputChanged,
            label = { Text("Motivo (ej. dolor de cabeza)") },
            isError = uiState.isDuplicate,
            supportingText = if (uiState.isDuplicate) {
                { Text("Ya tienes este medicamento en el historial con ese motivo.") }
            } else {
                null
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        if (uiState.suggestions.isNotEmpty()) {
            Column(modifier = Modifier.padding(top = 8.dp)) {
                uiState.suggestions.forEach { reason ->
                    val alreadyAdded = uiState.existingReasonNombres.any { it.equals(reason.nombre, true) }
                    ReasonSuggestionRow(
                        reason = reason,
                        alreadyAdded = alreadyAdded,
                        onClick = { viewModel.onSuggestionSelected(reason) },
                    )
                }
            }
        }

        Row(modifier = Modifier.padding(top = 24.dp)) {
            SecondaryButton(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) {
                Text("Cancelar")
            }
            GelButton(onClick = viewModel::save, enabled = uiState.canSave) {
                Text("Guardar")
            }
        }

        // Borrar ya no está a un toque en la lista: vive aquí, al abrir la entrada, y se confirma.
        if (uiState.isEditMode) {
            GroupedList(modifier = Modifier.padding(top = 32.dp)) {
                GroupedRow(onClick = { confirmDelete = true }, chevron = false) {
                    Text(
                        "Eliminar del historial",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Eliminar entrada") },
            text = { Text("¿Seguro que quieres eliminar \"${uiState.nombreMedicamento}\" del historial?") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.delete()
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun ReasonSuggestionRow(reason: Reason, alreadyAdded: Boolean, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(enabled = !alreadyAdded, onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(reason.nombre)
            if (alreadyAdded) {
                AddedChip("Ya añadido")
            }
        }
    }
}
