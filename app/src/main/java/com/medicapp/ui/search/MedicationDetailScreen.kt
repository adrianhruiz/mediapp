package com.medicapp.ui.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.ui.components.GelButton
import com.medicapp.model.AllergyMatch
import com.medicapp.model.AllergyType
import com.medicapp.model.MedicationDetail
import com.medicapp.ui.components.GroupedList
import com.medicapp.ui.components.GroupedRow
import com.medicapp.ui.components.Eyebrow
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.MedicationThumbnail
import com.medicapp.ui.components.SecondaryButton
import com.medicapp.ui.components.WarningLabel
import com.medicapp.ui.theme.DmMono

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDetailScreen(
    onAddToHistory: (MedicationDetail) -> Unit,
    onCreateReminder: (MedicationDetail) -> Unit,
    onBack: () -> Unit,
    viewModel: MedicationDetailViewModel = viewModel(factory = MedicationDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                title = { Eyebrow("Ficha del medicamento") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator() }

            uiState.errorMessage != null -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) { Text(uiState.errorMessage!!, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp)) }

            uiState.medication != null -> {
                val medication = uiState.medication!!
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    val (name, detail) = MedicationNames.split(medication.nombre)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        MedicationThumbnail(
                            fotoUrl = medication.fotoUrl,
                            seed = medication.nregistro,
                            modifier = Modifier.padding(end = 12.dp),
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(name, style = MaterialTheme.typography.headlineSmall)
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
                                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    // El aviso va lo primero, como la etiqueta que la farmacia pega en la caja: es lo
                    // único de la ficha que no se puede pasar por alto.
                    if (uiState.matches.isNotEmpty()) {
                        WarningLabel(
                            eyebrow = "Aviso de alergia",
                            title = "Desrecomendado",
                            rows = uiState.matches.map(::labelRow),
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }

                    // El estado va como una fila de lista agrupada, no como chip: un chip gris con
                    // icono se lee como un botón apagado al lado de los botones gel de verdad.
                    if (uiState.isInHistory) {
                        GroupedList(modifier = Modifier.padding(top = 16.dp)) {
                            GroupedRow {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Text("Ya en tu historial", style = MaterialTheme.typography.bodyLarge)
                                    Text(
                                        "Motivos: ${uiState.historyReasons.joinToString()}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = DmMono),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        "Componentes activos",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 4.dp),
                    )
                    Text(
                        "Toca un componente para marcar o quitar la alergia",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                    medication.principiosActivos.forEach { principioActivo ->
                        val isAllergic = uiState.allergies.any {
                            it.tipo == AllergyType.PRINCIPIO_ACTIVO && it.principioActivoId == principioActivo.id
                        }
                        FilterChip(
                            selected = isAllergic,
                            onClick = { viewModel.toggleIngredientAllergy(principioActivo) },
                            label = { Text(principioActivo.nombre) },
                            leadingIcon = if (isAllergic) {
                                { Icon(Icons.Default.Warning, contentDescription = null) }
                            } else {
                                null
                            },
                            colors = allergyChipColors(),
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }

                    Text(
                        "Familias (código ATC)",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 24.dp, bottom = 8.dp),
                    )
                    medication.atcs.forEach { atc ->
                        val isAllergic = uiState.allergies.any {
                            it.tipo == AllergyType.FAMILIA && it.atcCodigo == atc.codigo
                        }
                        FilterChip(
                            selected = isAllergic,
                            onClick = { viewModel.toggleFamiliaAllergy(atc) },
                            label = { Text("${atc.nombre} (${atc.codigo})") },
                            leadingIcon = if (isAllergic) {
                                { Icon(Icons.Default.Warning, contentDescription = null) }
                            } else {
                                null
                            },
                            colors = allergyChipColors(),
                            modifier = Modifier.padding(vertical = 4.dp),
                        )
                    }

                    GelButton(
                        onClick = { onAddToHistory(medication) },
                        enabled = uiState.canAddToHistory,
                        modifier = Modifier.fillMaxWidth().padding(top = 24.dp),
                    ) {
                        Text("Añadir al historial")
                    }
                    SecondaryButton(
                        onClick = { onCreateReminder(medication) },
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    ) {
                        Text("Crear pauta")
                    }
                }
            }
        }
    }
}

/** Un chip marcado como alergia es justo lo que causa la etiqueta: lleva su mismo coral. */
@Composable
private fun allergyChipColors() = FilterChipDefaults.filterChipColors(
    selectedContainerColor = MaterialTheme.colorScheme.tertiaryContainer,
    selectedLabelColor = MaterialTheme.colorScheme.onTertiaryContainer,
    selectedLeadingIconColor = MaterialTheme.colorScheme.onTertiaryContainer,
)

/** Lo que causa el aviso y de dónde sale, para una fila de la etiqueta. */
private fun labelRow(match: AllergyMatch): Pair<String, String> {
    val allergy = match.allergy
    return when (allergy.tipo) {
        AllergyType.PRINCIPIO_ACTIVO ->
            (allergy.principioActivoNombre?.let(MedicationNames::pretty) ?: match.descripcion) to "principio activo"
        AllergyType.FAMILIA ->
            (allergy.atcNombre?.let(MedicationNames::pretty) ?: match.descripcion) to
                listOfNotNull("familia", allergy.atcCodigo).joinToString(" · ")
    }
}
