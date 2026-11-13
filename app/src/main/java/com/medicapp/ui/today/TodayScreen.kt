package com.medicapp.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.medicapp.model.DoseStatus
import com.medicapp.ui.backup.SettingsButton
import com.medicapp.ui.components.BlisterCell
import com.medicapp.ui.components.DoseCellState
import com.medicapp.ui.components.GelButton
import com.medicapp.ui.components.GelTone
import com.medicapp.ui.components.GroupedList
import com.medicapp.ui.components.GroupedRow
import com.medicapp.ui.components.MedicationNames
import com.medicapp.ui.components.PillStyles
import com.medicapp.ui.theme.DmMono

/** Margen lateral y separación entre bloques: algo más de aire para que cada cosa respire. */
private val ScreenGutter = 20.dp

/** Ancho de la columna de horas y de la del marcador: las filas se alinean en vertical. */
private val HourColumn = 48.dp
private val MarkerColumn = 36.dp

private fun TodayDose.cellState(): DoseCellState = when (dose.status) {
    DoseStatus.PENDING -> DoseCellState.PENDING
    DoseStatus.TAKEN -> DoseCellState.TAKEN
    DoseStatus.SKIPPED -> DoseCellState.SKIPPED
}

/**
 * Hoy como una línea del día: lo hecho se encoge, la toma que toca se abre con su botón y lo que
 * viene espera con su hora. Sustituye a la cuadrícula del blíster con su tarjeta aparte, que
 * contaba lo mismo dos veces.
 */
@Composable
fun TodayScreen(
    contentPadding: PaddingValues,
    onOpenReminders: () -> Unit,
    onSearch: () -> Unit,
    onLogout: () -> Unit,
    viewModel: TodayViewModel = viewModel(factory = TodayViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
    var showEarlier by rememberSaveable { mutableStateOf(false) }
    val plan = state.plan
    // Sin elección del usuario se abre la que toca; con todo hecho, ninguna.
    val timeline = TodayTimeline.of(plan, showEarlier, selectedId)
    val open = timeline.open

    Box(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        LazyColumn(
            contentPadding = PaddingValues(start = ScreenGutter, end = ScreenGutter, top = 8.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(ScreenGutter),
        ) {
            item {
                TodayHeader(
                    nowMillis = state.nowMillis,
                    empty = !state.loading && plan.doses.isEmpty(),
                    snackbarHostState = snackbarHostState,
                    onLogout = onLogout,
                )
            }
            when {
                state.loading -> item {
                    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                plan.doses.isEmpty() -> item { EmptyToday(onSearch = onSearch, onOpenReminders = onOpenReminders) }
                else -> {
                    if (timeline.earlierCount > 0) {
                        item(key = "earlier") {
                            EarlierRow(
                                count = timeline.earlierCount,
                                expanded = showEarlier,
                                onToggle = { showEarlier = !showEarlier },
                            )
                        }
                    }
                    // Una sola pieza para que la línea vertical no se corte entre filas. Con todo
                    // plegado y nada de hoy, no se pinta: dejaba un hueco vacío.
                    if (timeline.visible.isNotEmpty()) item(key = "timeline") {
                        Column {
                            timeline.visible.forEachIndexed { index, dose ->
                                TimelineRow(
                                    dose = dose,
                                    nowMillis = state.nowMillis,
                                    open = dose == open,
                                    first = index == 0,
                                    last = index == timeline.visible.lastIndex,
                                    onOpen = { selectedId = dose.dose.id },
                                    onMarkTaken = { viewModel.markTaken(context, dose) },
                                )
                            }
                        }
                    }
                    if (plan.allDone) {
                        item {
                            Text(
                                "Ya no te queda ninguna toma hoy.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    item { MyRoutinesRow(onOpenReminders) }
                }
            }
        }
        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun TodayHeader(
    nowMillis: Long,
    empty: Boolean,
    snackbarHostState: SnackbarHostState,
    onLogout: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            if (nowMillis > 0) {
                Text(
                    dateLabel(nowMillis),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.secondary,
                )
            }
            // El recuento ya se ve en la línea: el título solo dice dónde estás.
            Text(if (empty) "Hoy, sin tomas" else "Hoy", style = MaterialTheme.typography.headlineMedium)
        }
        SettingsButton(snackbarHostState = snackbarHostState, onLogout = onLogout)
    }
}

/**
 * Una toma en la línea: hora, marcador sobre la línea vertical y lo que hay que saber. La que
 * está abierta enseña su tarjeta; las demás se abren al tocarlas.
 */
@Composable
private fun TimelineRow(
    dose: TodayDose,
    nowMillis: Long,
    open: Boolean,
    first: Boolean,
    last: Boolean,
    onOpen: () -> Unit,
    onMarkTaken: () -> Unit,
) {
    val (name, _) = MedicationNames.split(dose.schedule.nombreMedicamento)
    // En rojo solo lo que urge: la atrasada o la que toca dentro de una hora.
    val urgent = dose.overdue || (dose.pending && dose.dose.scheduledAtMillis - nowMillis < 3_600_000)
    val lineColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
            .then(
                if (open) {
                    Modifier
                } else {
                    Modifier
                        .clickable(role = Role.Tab, onClick = onOpen)
                        .semantics(mergeDescendants = true) {
                            contentDescription = "${dose.dose.scheduledAtMillis.hour()}, $name, ${dose.statusText().lowercase(spanish)}"
                        }
                },
            )
            .semantics { selected = open },
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            dose.timelineHour(nowMillis),
            style = (if (dose.carriedOver) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyMedium)
                .copy(fontFamily = DmMono),
            fontWeight = if (open && urgent) FontWeight.SemiBold else FontWeight.Normal,
            color = when {
                open && urgent -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.secondary
            },
            modifier = Modifier.width(HourColumn).padding(top = if (open) 26.dp else 22.dp),
        )
        // La línea vertical se dibuja detrás del marcador y une la fila con la anterior y la siguiente.
        Box(
            contentAlignment = Alignment.TopCenter,
            modifier = Modifier
                .width(MarkerColumn)
                .fillMaxHeight()
                .drawBehind {
                    val x = size.width / 2
                    val top = if (first) 30.dp.toPx() else 0f
                    val bottom = if (last) 30.dp.toPx() else size.height
                    if (bottom > top) drawLine(lineColor, Offset(x, top), Offset(x, bottom), strokeWidth = 2.dp.toPx())
                },
        ) {
            Box(Modifier.padding(top = if (open) 8.dp else 20.dp)) {
                Marker(dose = dose, open = open)
            }
        }
        if (open) {
            OpenDoseCard(
                dose = dose,
                name = name,
                nowMillis = nowMillis,
                urgent = urgent,
                onMarkTaken = onMarkTaken,
                modifier = Modifier.weight(1f).padding(vertical = 8.dp),
            )
        } else {
            Column(
                modifier = Modifier.weight(1f).heightIn(min = 64.dp).padding(vertical = 12.dp),
                verticalArrangement = Arrangement.Center,
            ) {
                val done = dose.dose.status != DoseStatus.PENDING
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (done) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (done) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                dose.timelineNote(nowMillis)?.let { note ->
                    Text(
                        note,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (dose.overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** Hecha: un check pequeño. Abierta: su celda de blíster. Por venir o atrasada: un punto. */
@Composable
private fun Marker(dose: TodayDose, open: Boolean) {
    when {
        open -> BlisterCell(
            state = dose.cellState(),
            pill = PillStyles.forMedication(dose.schedule.nregistro, dose.schedule.nombreMedicamento),
            width = 26.dp,
            highlighted = dose.pending,
        )
        dose.dose.status == DoseStatus.TAKEN -> Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(22.dp).background(MaterialTheme.colorScheme.secondary, CircleShape),
        ) {
            Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        else -> Box(
            Modifier
                .padding(top = 4.dp)
                .size(14.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerLowest, CircleShape)
                .border(
                    2.dp,
                    if (dose.overdue) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.outline,
                    CircleShape,
                ),
        )
    }
}

@Composable
private fun OpenDoseCard(
    dose: TodayDose,
    name: String,
    nowMillis: Long,
    urgent: Boolean,
    onMarkTaken: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(modifier = modifier, elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                dose.whenText(nowMillis),
                style = MaterialTheme.typography.labelLarge,
                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(name, style = MaterialTheme.typography.titleLarge)
            Text(
                detailText(dose),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (dose.pending) {
                GelButton(onClick = onMarkTaken, tone = GelTone.CORAL, modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    Text("Marcar como tomada", maxLines = 1)
                }
            }
        }
    }
}

/** La dosis en mono, como en el resto de la app, y el motivo en texto normal. */
@Composable
private fun detailText(dose: TodayDose) = buildAnnotatedString {
    val line = dose.detailLine()
    val detail = MedicationNames.split(dose.schedule.nombreMedicamento).second
    if (detail != null && line.startsWith(detail)) {
        withStyle(SpanStyle(fontFamily = DmMono)) { append(detail) }
        append(line.removePrefix(detail))
    } else {
        append(line)
    }
}

/**
 * Las tomas sin marcar de días anteriores, en una fila que se despliega: el recuento se dice una
 * vez, aquí, en vez de en la cabecera y en cada fila roja.
 */
@Composable
private fun EarlierRow(count: Int, expanded: Boolean, onToggle: () -> Unit) {
    GroupedList {
        GroupedRow(onClick = onToggle, chevron = false) {
            Text(
                if (count == 1) "1 toma sin marcar de días anteriores" else "$count tomas sin marcar de días anteriores",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.weight(1f),
            )
            Text(
                if (expanded) "Ocultar" else "Ver",
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/** Las pautas, como fila de lista bajo la línea: sustituye al icono de calendario. */
@Composable
private fun MyRoutinesRow(onOpenReminders: () -> Unit) {
    GroupedList {
        GroupedRow(onClick = onOpenReminders) {
            Text("Mis pautas", style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        }
    }
}

/** Sin tomas: el título ya dice «Hoy, sin tomas», así que aquí solo va qué hacer. */
@Composable
private fun EmptyToday(onSearch: () -> Unit, onOpenReminders: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            "Las tomas salen de tus pautas. Busca un medicamento y crea una pauta desde su ficha.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        GelButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
            Text("Buscar medicamento")
        }
        MyRoutinesRow(onOpenReminders)
    }
}
