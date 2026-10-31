package com.medicapp.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.CustomMedication
import com.medicapp.model.Medication
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.MedicationThumbnail
import com.medicapp.ui.components.ScreenHeader
import com.medicapp.ui.components.SearchHelpButton
import com.medicapp.ui.components.StatusSeal
import com.medicapp.ui.theme.DmMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    contentPadding: PaddingValues,
    onOpenDetail: (Medication) -> Unit,
    onAddCustomToHistory: (nregistro: String, nombre: String) -> Unit,
    onCreateCustomReminder: (nregistro: String, nombre: String) -> Unit,
    viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory),
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val savedNregistros by viewModel.savedNregistros.collectAsStateWithLifecycle()
    val historyNregistros by viewModel.historyNregistros.collectAsStateWithLifecycle()
    var customDraft by rememberSaveable { mutableStateOf<String?>(null) }

    customDraft?.let { draft ->
        CustomMedicationDialog(
            initialName = draft,
            onDismiss = { customDraft = null },
            onAddToHistory = { nregistro, nombre ->
                customDraft = null
                onAddCustomToHistory(nregistro, nombre)
            },
            onCreateReminder = { nregistro, nombre ->
                customDraft = null
                onCreateCustomReminder(nregistro, nombre)
            },
        )
    }
    val openCustomDraft = { customDraft = query.trim() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp),
    ) {
        ScreenHeader(title = "Buscar", eyebrow = "Medicamentos de CIMA · AEMPS", modifier = Modifier.padding(bottom = 12.dp))
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
            is SearchUiState.Empty -> {
                CustomMedicationCard(query = query, onClick = openCustomDraft)
                HintMessage("No se han encontrado medicamentos")
            }
            is SearchUiState.Error -> {
                CustomMedicationCard(query = query, onClick = openCustomDraft)
                HintMessage(state.message)
            }
            is SearchUiState.Success -> {
                if (state.isFromCache) {
                    Text(
                        "Sin acceso al buscador · mostrando resultados guardados",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    )
                }
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
                            isSaved = medication.nregistro in savedNregistros,
                            isInHistory = medication.nregistro in historyNregistros,
                            onClick = { onOpenDetail(medication) },
                        )
                    }
                    item(key = "custom") {
                        CustomMedicationCard(query = query, onClick = openCustomDraft)
                    }
                }
            }
        }
    }
}

/** Salida para lo que CIMA no tiene: una pomada, un remedio sin registro... */
@Composable
private fun CustomMedicationCard(query: String, onClick: () -> Unit) {
    OutlinedCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.padding(end = 12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text("¿No está en la lista?", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Añade «${query.trim()}» como medicamento propio",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CustomMedicationDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onAddToHistory: (nregistro: String, nombre: String) -> Unit,
    onCreateReminder: (nregistro: String, nombre: String) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    val nregistro = CustomMedication.idFor(name)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Medicamento propio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Para lo que no está en CIMA, como una pomada. No tendrá ficha ni avisos de alergia.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Row {
                TextButton(enabled = nregistro != null, onClick = { nregistro?.let { onCreateReminder(it, name.trim()) } }) {
                    Text("Recordatorio")
                }
                TextButton(enabled = nregistro != null, onClick = { nregistro?.let { onAddToHistory(it, name.trim()) } }) {
                    Text("Al historial")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        },
    )
}

@Composable
private fun HintMessage(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
    }
}

@Composable
private fun MedicationResultCard(
    medication: Medication,
    isSaved: Boolean,
    isInHistory: Boolean,
    onClick: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Los estados van como sellos sobre la foto y no como chips debajo: no hacen más alta
            // la tarjeta y no se confunden con botones.
            Box(modifier = Modifier.padding(end = 12.dp)) {
                MedicationThumbnail(fotoUrl = medication.fotoUrl, seed = medication.nregistro)
                Row(
                    modifier = Modifier.align(Alignment.BottomEnd).offset(x = 6.dp, y = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    if (isSaved) StatusSeal(Icons.Default.Bookmark, "Ya lo tienes guardado")
                    if (isInHistory) StatusSeal(Icons.Default.Check, "Ya en tu historial")
                }
            }
            val (name, detail) = MedicationNames.split(medication.nombre)
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(name, style = MaterialTheme.typography.titleMedium)
                detail?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = DmMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                // La dosis de CIMA suele repetir la del nombre; solo se enseña si añade algo.
                medication.dosis?.takeIf { detail?.contains(it, ignoreCase = true) != true }?.let(MedicationNames::detail)?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = DmMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                medication.labtitular?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Icon(Icons.Default.ArrowForward, contentDescription = null)
        }
    }
}
