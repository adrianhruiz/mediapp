package com.medicapp.ui.history

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medicapp.model.TakenEntry
import com.medicapp.ui.components.ScreenHeader

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
        PrimaryTabRow(selectedTabIndex = tab, modifier = Modifier.padding(top = 8.dp)) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Semana") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Medicamentos") })
        }
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
