package com.medicapp.ui.allergies

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import com.medicapp.model.AtcCode
import com.medicapp.model.PrincipioActivo
import com.medicapp.ui.components.SearchHelpButton

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllergiesScreen(
    contentPadding: PaddingValues,
    viewModel: AllergiesViewModel = viewModel(factory = AllergiesViewModel.Factory),
) {
    val allergies by viewModel.allergies.collectAsStateWithLifecycle()
    val searchMode by viewModel.searchMode.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchState by viewModel.searchState.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Text("Tus alergias", style = MaterialTheme.typography.titleLarge)
        if (allergies.isEmpty()) {
            Text(
                "Todavía no has marcado ninguna alergia",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
        } else {
            Column(modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)) {
                allergies.forEach { allergy ->
                    AllergyRow(allergy = allergy, onDelete = { viewModel.delete(allergy) })
                }
            }
        }

        Text("Añadir alergia", style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(top = 8.dp))

        TabRow(selectedTabIndex = searchMode.ordinal, modifier = Modifier.padding(top = 8.dp)) {
            Tab(
                selected = searchMode == AllergySearchMode.PRINCIPIO_ACTIVO,
                onClick = { viewModel.onSearchModeChanged(AllergySearchMode.PRINCIPIO_ACTIVO) },
                text = { Text("Componente activo") },
            )
            Tab(
                selected = searchMode == AllergySearchMode.FAMILIA,
                onClick = { viewModel.onSearchModeChanged(AllergySearchMode.FAMILIA) },
                text = { Text("Familia (ATC)") },
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChanged,
            modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
            label = {
                Text(
                    if (searchMode == AllergySearchMode.PRINCIPIO_ACTIVO) {
                        "Buscar componente activo (ej. Ibuprofeno)"
                    } else {
                        "Buscar familia (ej. Penicilina)"
                    },
                )
            },
            trailingIcon = {
                if (searchMode == AllergySearchMode.PRINCIPIO_ACTIVO) {
                    SearchHelpButton(
                        title = "¿Qué es un componente activo?",
                        description = "Es la sustancia que hace efecto dentro del medicamento, " +
                            "independientemente de su marca comercial. Por ejemplo, Xazal contiene " +
                            "el componente activo Levocetirizina.",
                        examples = listOf("Ibuprofeno", "Amoxicilina", "Diclofenaco", "Paracetamol"),
                    )
                } else {
                    SearchHelpButton(
                        title = "¿Qué es una familia (ATC)?",
                        description = "Es un grupo de medicamentos con efectos similares. Marcar una " +
                            "alergia de familia avisa sobre todos los medicamentos de ese grupo, no solo " +
                            "uno. Puedes buscar por nombre o directamente por código ATC.",
                        examples = listOf("penicilina", "cefalosporina", "pirazolona", "M01A", "J01C"),
                    )
                }
            },
            supportingText = {
                Text(
                    if (searchMode == AllergySearchMode.PRINCIPIO_ACTIVO) {
                        "Nombre del principio activo. Ej: Amoxicilina, Diclofenaco"
                    } else {
                        "Nombre o código ATC del grupo. Ej: penicilina, J01C"
                    },
                )
            },
            singleLine = true,
        )

        Column(modifier = Modifier.padding(top = 8.dp)) {
            when (val state = searchState) {
                is CatalogSearchUiState.Idle -> Unit
                is CatalogSearchUiState.Loading -> Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.Center,
                ) { CircularProgressIndicator() }
                is CatalogSearchUiState.Empty -> Text(
                    "No se han encontrado resultados",
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                is CatalogSearchUiState.Error -> Text(
                    state.message,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                )
                is CatalogSearchUiState.IngredientResults -> state.items.forEach { item ->
                    val alreadyAdded = allergies.any {
                        it.tipo == AllergyType.PRINCIPIO_ACTIVO && it.principioActivoId == item.id
                    }
                    IngredientResultRow(
                        item = item,
                        alreadyAdded = alreadyAdded,
                        onAdd = { viewModel.addIngredientAllergy(item) },
                    )
                }
                is CatalogSearchUiState.FamilyResults -> state.items.forEach { item ->
                    val alreadyAdded = allergies.any { it.tipo == AllergyType.FAMILIA && it.atcCodigo == item.codigo }
                    FamilyResultRow(
                        item = item,
                        alreadyAdded = alreadyAdded,
                        onAdd = { viewModel.addFamilyAllergy(item) },
                    )
                }
            }
        }
    }
}

@Composable
private fun AllergyRow(allergy: Allergy, onDelete: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    if (allergy.tipo == AllergyType.PRINCIPIO_ACTIVO) "Componente activo" else "Familia",
                    style = MaterialTheme.typography.labelMedium,
                )
                Text(
                    allergy.principioActivoNombre ?: allergy.atcNombre.orEmpty(),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Eliminar alergia")
            }
        }
    }
}

@Composable
private fun IngredientResultRow(item: PrincipioActivo, alreadyAdded: Boolean, onAdd: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = !alreadyAdded, onClick = onAdd),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(item.nombre, style = MaterialTheme.typography.bodyLarge)
            if (alreadyAdded) {
                Text("Ya añadida", style = MaterialTheme.typography.labelMedium)
            } else {
                Icon(Icons.Default.Add, contentDescription = "Añadir alergia")
            }
        }
    }
}

@Composable
private fun FamilyResultRow(item: AtcCode, alreadyAdded: Boolean, onAdd: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clickable(enabled = !alreadyAdded, onClick = onAdd),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(item.nombre, style = MaterialTheme.typography.bodyLarge)
                Text(item.codigo, style = MaterialTheme.typography.bodySmall)
            }
            if (alreadyAdded) {
                Text("Ya añadida", style = MaterialTheme.typography.labelMedium)
            } else {
                Icon(Icons.Default.Add, contentDescription = "Añadir alergia")
            }
        }
    }
}
