package com.medicapp.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.AllergyType
import com.medicapp.model.AtcCode
import com.medicapp.model.MedicationDetail
import com.medicapp.model.PrincipioActivo
import com.medicapp.ui.components.AddedChip
import com.medicapp.ui.components.GelButton
import com.medicapp.ui.components.GroupHeader
import com.medicapp.ui.components.GroupedList
import com.medicapp.ui.components.GroupedRow
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.MedicationThumbnail
import com.medicapp.ui.theme.DmMono

private val chevronTint = Color(0xFF9AA2AE)

@Composable
fun MedicationDetailScreen(
    onAddToHistory: (MedicationDetail) -> Unit,
    onCreateReminder: (MedicationDetail) -> Unit,
    onBack: () -> Unit,
    viewModel: MedicationDetailViewModel = viewModel(factory = MedicationDetailViewModel.Factory),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Sin Scaffold propio: los márgenes de las barras del sistema se ponen aquí.
    Column(modifier = Modifier.fillMaxSize().systemBarsPadding()) {
        IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
        }
        when {
            uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }

            uiState.errorMessage != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(uiState.errorMessage!!, textAlign = TextAlign.Center, modifier = Modifier.padding(24.dp))
            }

            uiState.medication != null -> DetailContent(
                medication = uiState.medication!!,
                uiState = uiState,
                onToggleIngredient = viewModel::toggleIngredientAllergy,
                onToggleFamily = viewModel::toggleFamiliaAllergy,
                onAddToHistory = onAddToHistory,
                onCreateReminder = onCreateReminder,
            )
        }
    }
}

@Composable
private fun DetailContent(
    medication: MedicationDetail,
    uiState: MedicationDetailUiState,
    onToggleIngredient: (PrincipioActivo) -> Unit,
    onToggleFamily: (AtcCode) -> Unit,
    onAddToHistory: (MedicationDetail) -> Unit,
    onCreateReminder: (MedicationDetail) -> Unit,
) {
    var moreInfoOpen by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // El aviso va lo primero y en coral lleno: es lo único de la ficha que no se puede pasar
        // por alto. Por eso el bloqueo de «Añadir al historial» no repite la explicación.
        if (uiState.matches.isNotEmpty()) {
            AllergyStrip(uiState.matches.map { it.descripcion })
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MedicationThumbnail(fotoUrl = medication.fotoUrl, seed = medication.nregistro)
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                val (name, presentation) = nameAndPresentation(medication.nombre, medication.dosis)
                Text(name, style = MaterialTheme.typography.headlineSmall)
                presentation?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = DmMono),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (uiState.isInHistory) {
                    AddedChip("Ya en tu historial")
                    Text(
                        "Motivos registrados: ${uiState.historyReasons.joinToString()}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        if (medication.principiosActivos.isNotEmpty()) {
            Column {
                GroupHeader("Componentes activos")
                GroupedList {
                    medication.principiosActivos.forEachIndexed { index, principioActivo ->
                        val isAllergic = uiState.allergies.any {
                            it.tipo == AllergyType.PRINCIPIO_ACTIVO && it.principioActivoId == principioActivo.id
                        }
                        AllergySwitchRow(
                            label = MedicationNames.pretty(principioActivo.nombre),
                            code = null,
                            checked = isAllergic,
                            divider = index > 0,
                            onToggle = { onToggleIngredient(principioActivo) },
                        )
                    }
                }
            }
        }

        Column {
            GroupedList {
                GroupedRow(
                    chevron = false,
                    modifier = Modifier.clickable(role = Role.Button) { moreInfoOpen = !moreInfoOpen },
                ) {
                    Text("Más información", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                    Icon(
                        if (moreInfoOpen) Icons.Default.KeyboardArrowDown else Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = null,
                        tint = chevronTint,
                    )
                }
                if (moreInfoOpen) {
                    medication.labtitular?.let { lab ->
                        GroupedRow(divider = true) {
                            Text("Laboratorio", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                            Text(lab, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    // Las familias ATC se siguen pudiendo marcar como alergia desde aquí.
                    medication.atcs.forEach { atc ->
                        val isAllergic = uiState.allergies.any {
                            it.tipo == AllergyType.FAMILIA && it.atcCodigo == atc.codigo
                        }
                        AllergySwitchRow(
                            label = MedicationNames.pretty(atc.nombre),
                            code = atc.codigo,
                            checked = isAllergic,
                            divider = true,
                            onToggle = { onToggleFamily(atc) },
                        )
                    }
                }
            }
        }

        GelButton(onClick = { onCreateReminder(medication) }, modifier = Modifier.fillMaxWidth()) {
            Text("Crear pauta")
        }
        GroupedList(modifier = Modifier.alpha(if (uiState.canAddToHistory) 1f else 0.45f)) {
            GroupedRow(
                modifier = Modifier.clickable(enabled = uiState.canAddToHistory, role = Role.Button) {
                    onAddToHistory(medication)
                },
                chevron = true,
            ) {
                Text("Añadir al historial", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            }
        }
    }
}

/** Franja coral única arriba de la ficha: qué contiene a lo que eres alérgico. */
@Composable
private fun AllergyStrip(descriptions: List<String>) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiary, MaterialTheme.shapes.medium)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiary)
        Column {
            Text(
                "Desrecomendado",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onTertiary,
            )
            Text(
                "Contiene ${descriptions.joinToString()}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onTertiary,
            )
        }
    }
}

/** Fila con interruptor «Alérgico»: se explica sola, sin instrucciones aparte. */
@Composable
private fun AllergySwitchRow(label: String, code: String?, checked: Boolean, divider: Boolean, onToggle: () -> Unit) {
    GroupedRow(
        divider = divider,
        chevron = false,
        modifier = Modifier.toggleable(value = checked, role = Role.Switch, onValueChange = { onToggle() }),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge)
            code?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = DmMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Text("Alérgico", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.tertiary),
        )
    }
}
