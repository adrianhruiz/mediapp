package com.medicapp.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medicapp.model.TakenEntry
import com.medicapp.ui.components.ScreenHeader
import com.medicapp.ui.components.SegmentedControl

/**
 * Historial con dos vistas: la semana en blíster, para ver de un vistazo qué tomas se escaparon,
 * y la lista de medicamentos tomados con su motivo, que es la que había antes.
 */
@Composable
fun HistoryHubScreen(
    contentPadding: PaddingValues,
    onEditEntry: (TakenEntry) -> Unit,
    onOpenMedication: (TakenEntry) -> Unit,
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().padding(contentPadding)) {
        ScreenHeader(title = "Historial", modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp))
        SegmentedControl(
            options = listOf("Semana", "Medicamentos"),
            selectedIndex = tab,
            onSelect = { tab = it },
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp),
        )
        if (tab == 0) {
            WeekHistoryContent()
        } else {
            HistoryScreen(
                contentPadding = PaddingValues(0.dp),
                onEditEntry = onEditEntry,
                onOpenMedication = onOpenMedication,
            )
        }
    }
}
