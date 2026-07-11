package com.medicapp.ui.search

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.Medication
import com.medicapp.ui.components.MedicationThumbnail
import com.medicapp.ui.components.SearchHelpButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    onOpenDetail: (Medication) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChanged,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Buscar medicamento") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            trailingIcon = {
                SearchHelpButton(
                    title = "¿Qué puedo buscar aquí?",
                    description = "Puedes escribir el nombre comercial del medicamento o su principio activo.",
                    examples = listOf("Xazal", "Paracetamol", "Ibuprofeno 600 mg"),
                )
            },
            supportingText = { Text("Nombre comercial o principio activo. Ej: Xazal, Ibuprofeno") },
            singleLine = true,
        )

        androidx.compose.foundation.layout.Spacer(modifier = Modifier.padding(top = 8.dp))

        when (val state = uiState) {
            is SearchUiState.Idle -> HintMessage("Escribe el nombre de un medicamento para buscar")
            is SearchUiState.Loading -> Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            is SearchUiState.Empty -> HintMessage("No se han encontrado medicamentos")
            is SearchUiState.Error -> HintMessage(state.message)
            is SearchUiState.Success -> {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (state.totalFilas > state.results.size) {
                        item {
                            Text(
                                "Mostrando ${state.results.size} de ${state.totalFilas} resultados. Refina la búsqueda para más precisión.",
                                modifier = Modifier.padding(vertical = 4.dp),
                            )
                        }
                    }
                    items(state.results, key = { it.nregistro }) { medication ->
                        MedicationResultCard(
                            medication = medication,
                            isSaved = medication.nregistro in state.savedNregistros,
                            onClick = { onOpenDetail(medication) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HintMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun MedicationResultCard(medication: Medication, isSaved: Boolean, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MedicationThumbnail(fotoUrl = medication.fotoUrl, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(medication.nombre, style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                medication.labtitular?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
                medication.dosis?.let { Text(it, style = androidx.compose.material3.MaterialTheme.typography.bodySmall) }
                if (isSaved) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = { Text("Ya lo tienes guardado") },
                        leadingIcon = { Icon(Icons.Default.Bookmark, contentDescription = null) },
                        colors = AssistChipDefaults.assistChipColors(
                            disabledContainerColor = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer,
                            disabledLabelColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                            disabledLeadingIconContentColor = androidx.compose.material3.MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}
