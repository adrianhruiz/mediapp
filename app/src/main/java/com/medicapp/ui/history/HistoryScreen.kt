package com.medicapp.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
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
import com.medicapp.model.TakenEntry
import com.medicapp.ui.components.GroupedList
import com.medicapp.ui.components.GroupedRow
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.MedicationThumbnail
import com.medicapp.ui.components.OnBottomReached
import androidx.compose.ui.text.style.TextOverflow
import java.time.LocalDate

/**
 * Lista de medicamentos tomados, agrupada por día y con dos líneas por toma. Tocar una entrada la abre para editar su
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
    val hasAnyEntries by viewModel.hasAnyEntries.collectAsStateWithLifecycle()
    val hasMore by viewModel.hasMore.collectAsStateWithLifecycle()
    val isOffline by viewModel.isOffline.collectAsStateWithLifecycle()
    val days = remember(entries) { HistoryDays.group(entries, LocalDate.now()) }
    val listState = rememberLazyListState()
    listState.OnBottomReached(onLoadMore = viewModel::loadMore)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (hasAnyEntries) {
            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChanged,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Medicamento o motivo") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
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
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                items(days, key = { it.day.toString() }) { group ->
                    Column {
                        // En frase normal, no en mayúsculas mono: «Hoy», «Ayer», «Martes, 3 de noviembre».
                        Text(
                            group.label,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.padding(start = 4.dp, bottom = 8.dp),
                        )
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


@Composable
private fun HistoryEntryRow(
    entry: TakenEntry,
    divider: Boolean,
    onOpen: () -> Unit,
    onOpenMedication: () -> Unit,
) {
    val (name, _) = MedicationNames.split(entry.nombreMedicamento)
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
        // Dos líneas: el día ya va en la cabecera del grupo, así que basta la hora y el motivo.
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                remember(entry.timestampMillis, entry.reasonNombre) { HistoryDays.note(entry) },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

