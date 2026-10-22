package com.medicapp.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.medicapp.ui.components.BlisterCell
import com.medicapp.ui.components.DoseCellState
import com.medicapp.ui.components.FoilCard
import com.medicapp.ui.components.PillStyles

/** Un blíster pequeño con una toma hecha y la siguiente marcada: la idea de la app de un vistazo. */
@Composable
fun BrandMark(modifier: Modifier = Modifier) {
    val pill = PillStyles.forMedication("medicapp", "cápsulas")
    FoilCard(modifier = modifier, contentPadding = PaddingValues(horizontal = 10.dp, vertical = 8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            BlisterCell(DoseCellState.TAKEN, pill, width = 26.dp)
            BlisterCell(DoseCellState.PENDING, pill, width = 26.dp, highlighted = true)
            BlisterCell(DoseCellState.PENDING, pill, width = 26.dp)
        }
    }
}
