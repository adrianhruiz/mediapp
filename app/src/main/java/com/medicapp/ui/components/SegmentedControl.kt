package com.medicapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medicapp.ui.theme.BlisterTheme

// Mismos tonos que la celda activa de la barra inferior: el segmento elegido queda hundido.
private val sunkenTop = Color(0xFFBFC5CE)
private val sunkenBottom = Color(0xFFDDE1E6)
private val sunkenShade = Color(0x4D18202C)

/**
 * Control segmentado de 2010: una fila de celdas pegadas en la que la elegida está hundida, igual
 * que la pestaña activa de la barra inferior. Se usa en Historial (Semana/Medicamentos) y en la
 * frecuencia de una pauta (8 h · 12 h · 24 h · Otra).
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = BlisterTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.verticalGradient(listOf(colors.glass, colors.glassEdge)))
            .border(1.dp, colors.glassEdge, shape)
            .selectableGroup(),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .then(
                        if (selected) {
                            Modifier
                                .background(Brush.verticalGradient(listOf(sunkenTop, sunkenBottom)))
                                .drawWithContent {
                                    drawContent()
                                    // Sombra interior arriba: es lo que hace que parezca hundida.
                                    drawRect(
                                        Brush.verticalGradient(
                                            listOf(sunkenShade, Color.Transparent),
                                            endY = size.height * 0.45f,
                                        ),
                                    )
                                }
                        } else {
                            Modifier
                        },
                    )
                    .then(if (index > 0) Modifier.border(0.5.dp, colors.glassEdge) else Modifier)
                    .selectable(selected = selected, role = Role.Tab, onClick = { onSelect(index) })
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(shadow = EngravedShadow),
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
