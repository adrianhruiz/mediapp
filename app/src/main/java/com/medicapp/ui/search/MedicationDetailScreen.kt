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
import androidx.compose.foundation.relocation.BringIntoViewRequester
import androidx.compose.foundation.relocation.bringIntoViewRequester
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
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
import com.medicapp.ui.components.Eyebrow
import com.medicapp.ui.components.GelButton
import com.medicapp.ui.components.GroupHeader
import com.medicapp.ui.components.GroupedList
import com.medicapp.ui.components.GroupedRow
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.MedicationPhotoViewer
import com.medicapp.ui.components.MedicationThumbnail
import com.medicapp.ui.components.StatusSeal
import com.medicapp.ui.theme.DmMono
import kotlinx.coroutines.launch

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
    var showPhoto by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Tocar el aviso baja hasta la sección donde está el interruptor que lo causa.
    val ingredientsSection = remember { BringIntoViewRequester() }
    val familiesSection = remember { BringIntoViewRequester() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // El aviso va lo primero y es lo único coral de la ficha. No repite el nombre de la
        // alergia, que ya está en su interruptor más abajo; por eso el bloqueo de «Añadir al
        // historial» y de «Crear pauta» tampoco repite la explicación.
        if (uiState.matches.isNotEmpty()) {
            AllergyWarning(
                count = uiState.matches.size,
                onClick = {
                    // Componentes antes que familias; las familias viven dentro de «Más
                    // información», así que primero hay que abrirla.
                    val toIngredients = uiState.matches.any { it.allergy.tipo == AllergyType.PRINCIPIO_ACTIVO }
                    if (!toIngredients) moreInfoOpen = true
                    scope.launch { (if (toIngredients) ingredientsSection else familiesSection).bringIntoView() }
                },
            )
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // Solo la ficha deja ampliar la foto: en las listas, tocar la fila ya navega.
            val fotoUrl = medication.fotoUrl
            MedicationThumbnail(
                fotoUrl = fotoUrl,
                seed = medication.nregistro,
                modifier = if (fotoUrl == null) {
                    Modifier
                } else {
                    Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClickLabel = "Ampliar foto") { showPhoto = true }
                },
            )
            if (showPhoto && fotoUrl != null) {
                MedicationPhotoViewer(thumbnailUrl = fotoUrl, onDismiss = { showPhoto = false })
            }
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
            }
        }

        // El estado va como una fila de lista agrupada, no como chip: un chip gris con icono se
        // lee como un botón apagado al lado de los botones gel de verdad.
        if (uiState.isInHistory) {
            GroupedList {
                GroupedRow(chevron = false) {
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

        if (medication.principiosActivos.isNotEmpty()) {
            Column {
                GroupHeader("Componentes activos", modifier = Modifier.bringIntoViewRequester(ingredientsSection))
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

        Column(modifier = Modifier.bringIntoViewRequester(familiesSection)) {
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
                        GroupedRow(divider = true, chevron = false) {
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

        // Un desrecomendado tampoco se programa: sería recordar tomar algo que no se debe.
        GelButton(
            onClick = { onCreateReminder(medication) },
            enabled = uiState.canCreateSchedule,
            modifier = Modifier.fillMaxWidth(),
        ) {
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

/**
 * Aviso de desrecomendado: una sola fila de lista agrupada, en coral suave. No repite el nombre
 * de la alergia, que ya está en su interruptor más abajo; tocarla lleva hasta él.
 */
@Composable
private fun AllergyWarning(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val ink = MaterialTheme.colorScheme.onTertiaryContainer
    Column(modifier = modifier) {
        Eyebrow("Alergias", color = ink, modifier = Modifier.padding(start = 4.dp, bottom = 6.dp))
        GroupedList {
            GroupedRow(modifier = Modifier.background(warningLead), onClick = onClick) {
                StatusSeal(Icons.Default.Warning, description = "Alerta", tint = MaterialTheme.colorScheme.error)
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        "Desrecomendado para ti",
                        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                        color = ink,
                    )
                    Text(
                        if (count == 1) "Por una alergia marcada abajo" else "Por $count alergias marcadas abajo",
                        style = MaterialTheme.typography.bodySmall,
                        color = warningInkMuted,
                    )
                }
            }
        }
    }
}

// Fondo de la fila del aviso: un coral apenas tintado, con el mismo degradado vertical que las
// filas en relieve.
private val warningLead = Brush.verticalGradient(listOf(Color(0xFFFFF4F3), Color(0xFFFFE7E4)))
private val warningInkMuted = Color(0xFF9A4A43)
