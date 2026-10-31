package com.medicapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

private val groupEdge = Color(0xFFC5CBD3)
private val rowLine = Color(0xFFE4E7EB)
private val chevronTint = Color(0xFF9AA2AE)

// Raya fina vertical del fondo de las listas agrupadas de 2010: dos grises casi iguales que
// se alternan y quitan el aspecto de color plano sin llegar a distraer.
private val stripeLight = Color(0xFFF1F2F4)
private val stripeDark = Color(0xFFEBEDF0)

/** Fondo de raya fina. Se pone una vez en la raíz y las pantallas lo dejan ver. */
fun Modifier.pinstripe(): Modifier = drawBehind {
    val period = 7.dp.toPx()
    drawRect(
        Brush.horizontalGradient(
            0f to stripeLight,
            5f / 7f to stripeLight,
            5f / 7f to stripeDark,
            1f to stripeDark,
            startX = 0f,
            endX = period,
            tileMode = TileMode.Repeated,
        ),
    )
}

/**
 * Lista agrupada: una sola tarjeta blanca con las filas separadas por una línea fina, en vez de
 * una tarjeta por elemento. Las filas van dentro con [GroupedRow].
 */
@Composable
fun GroupedList(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = MaterialTheme.shapes.medium
    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(1.dp, shape)
            .clip(shape)
            .background(Color.White)
            .border(1.dp, groupEdge, shape),
        content = content,
    )
}

/**
 * Fila de una [GroupedList]. [divider] dibuja la línea de separación encima (todas menos la
 * primera); con [onClick] la fila entera se pulsa y, si [chevron], lleva la flecha › al final.
 */
@Composable
fun GroupedRow(
    modifier: Modifier = Modifier,
    divider: Boolean = false,
    onClick: (() -> Unit)? = null,
    chevron: Boolean = onClick != null,
    content: @Composable RowScope.() -> Unit,
) {
    if (divider) HorizontalDivider(color = rowLine, modifier = Modifier.padding(start = 16.dp))
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier)
            .heightIn(min = 52.dp)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        content()
        if (chevron) {
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = chevronTint)
        }
    }
}

/** Cabecera de grupo: antetítulo grabado sobre el fondo, encima de una [GroupedList]. */
@Composable
fun GroupHeader(text: String, modifier: Modifier = Modifier) {
    Eyebrow(text = text, modifier = modifier.padding(start = 4.dp, bottom = 6.dp))
}
