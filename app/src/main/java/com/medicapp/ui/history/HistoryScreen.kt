package com.medicapp.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.CustomMedication
import com.medicapp.model.Reason
import com.medicapp.model.TakenEntry
import com.medicapp.ui.components.GroupHeader
import com.medicapp.ui.components.GroupedList
import com.medicapp.ui.components.GroupedRow
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.MedicationThumbnail
import com.medicapp.ui.components.OnBottomReached
import com.medicapp.ui.theme.DmMono
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Lista de medicamentos tomados, agrupada por mes. Tocar una entrada la abre para editar su
 * motivo o borrarla; la miniatura abre la ficha del medicamento cuando la hay.
 */
@Composable
fun HistoryScreen(
    contentPadding: PaddingValues,
    onEditEntry: (TakenEntry) -> Unit,
    onOpenMedication: (TakenEntry) -> Unit,
    viewModel: HistoryViewModel = viewModel(factory = HistoryViewModel.Factory),
) {
    val entries by viewModel.entries.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val reasons by viewModel.reasons.collectAsStateWithLifecycle()
    val selectedReasonId by viewModel.selectedReasonId.collectAsStateWithLifecycle()
    val hasAnyEntries by viewModel.hasAnyEntries.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val months = remember(entries) { HistoryMonths.group(entries) }
    val listState = rememberLazyListState()
    listState.OnBottomReached(onLoadMore = viewModel::loadMore)

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
                placeholder = { Text("Buscar por medicamento o motivo") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
            )
            ReasonChips(
                reasons = reasons,
                selectedReasonId = selectedReasonId,
                onAll = { viewModel.onReasonFilterChanged(null) },
                onReason = viewModel::onReasonChipClicked,
                modifier = Modifier.padding(top = 8.dp),
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
            if (isOffline) {
                Text(
                    "Sin conexión · mostrando datos guardados",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                )
            }
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(months, key = { it.month.toString() }) { group ->
                    Column {
                        GroupHeader(group.label)
                        GroupedList {
                            group.entries.forEachIndexed { index, entry ->
                                HistoryEntryRow(
                                    entry = entry,
                                    divider = index > 0,
                                    onOpen = { onEditEntry(entry) },
                                    onOpenMedication = { onOpenMedication(entry) },
                                )
                            }
                        }
                    }
                }
                if (hasMore) {
                    item {
                        Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator()
                        }
                    }
                }
            }
        }
    }
}

private val dateFormat = DateTimeFormatter.ofPattern("d MMM · HH:mm", Locale.forLanguageTag("es-ES"))

@Composable
private fun HistoryEntryRow(
    entry: TakenEntry,
    divider: Boolean,
    onOpen: () -> Unit,
    onOpenMedication: () -> Unit,
) {
    val (name, detail) = MedicationNames.split(entry.nombreMedicamento)
    // Los medicamentos propios no están en CIMA: no hay ficha que abrir.
    val hasSheet = entry.nregistro.isNotBlank() && !CustomMedication.isCustom(entry.nregistro)
    GroupedRow(
        divider = divider,
        onClick = onOpen,
        modifier = Modifier.semantics { contentDescription = "Abrir entrada de $name" },
    ) {
        MedicationThumbnail(
            fotoUrl = entry.fotoUrl,
            seed = entry.nregistro,
            modifier = Modifier
                .clickable(enabled = hasSheet, role = Role.Button, onClick = onOpenMedication)
                .semantics { contentDescription = "Ver ficha de $name" },
        )
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium)
            detail?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = DmMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text(entry.reasonNombre, style = MaterialTheme.typography.bodyMedium)
            val formattedDate = remember(entry.timestampMillis) {
                Instant.ofEpochMilli(entry.timestampMillis)
                    .atZone(ZoneId.systemDefault())
                    .format(dateFormat)
                    .replace(".", "")
            }
            Text(
                formattedDate,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = DmMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Filtro por motivo en chips: «Todos» y uno por motivo; tocar el elegido lo quita. */
@Composable
private fun ReasonChips(
    reasons: List<Reason>,
    selectedReasonId: String?,
    onAll: () -> Unit,
    onReason: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (reasons.isEmpty()) return
    Row(
        modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FilterChip(selected = selectedReasonId == null, onClick = onAll, label = { Text("Todos") })
        reasons.forEach { reason ->
            FilterChip(
                selected = reason.id == selectedReasonId,
                onClick = { onReason(reason.id) },
                label = { Text(reason.nombre) },
            )
        }
    }
}
