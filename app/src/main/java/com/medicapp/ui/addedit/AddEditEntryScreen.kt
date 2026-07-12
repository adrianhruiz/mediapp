package com.medicapp.ui.addedit

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.Reason

@Composable
fun AddEditEntryScreen(
    onDone: () -> Unit,
    onCancel: () -> Unit,
    viewModel: AddEditEntryViewModel = viewModel(factory = AddEditEntryViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.isSaved) {
        if (uiState.isSaved) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
    ) {
        Text(
            if (uiState.isEditMode) "Editar entrada" else "Añadir al historial",
            style = MaterialTheme.typography.titleLarge,
        )
        Text(
            uiState.nombreMedicamento,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
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
                    ReasonSuggestionRow(reason = reason, onClick = { viewModel.onSuggestionSelected(reason) })
                }
            }
        }

        Row(modifier = Modifier.padding(top = 24.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.padding(end = 8.dp)) {
                Text("Cancelar")
            }
            Button(onClick = viewModel::save, enabled = uiState.canSave) {
                Text("Guardar")
            }
        }
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
