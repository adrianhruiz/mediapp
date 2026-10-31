package com.medicapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.medicapp.ui.theme.DmMono

// Pegatina de advertencia de farmacia: papel coral, cinta de rayas arriba y una costura
// discontinua por dentro. Los tonos intermedios entre CoralSoft y Coral solo viven aquí.
private val labelEdge = Color(0xFFF2B3AD)
private val labelStitch = Color(0xFFE8A49E)
private val labelInkMuted = Color(0xFF9A4A43)
private val labelShape = RoundedCornerShape(6.dp)

/**
 * Etiqueta de advertencia pegada en la ficha, algo torcida, como las de las cajas de la farmacia.
 * Cada fila de [rows] es lo que causa el aviso y, a la derecha y en mono, de dónde sale.
 */
@Composable
fun WarningLabel(
    eyebrow: String,
    title: String,
    rows: List<Pair<String, String>>,
    modifier: Modifier = Modifier,
) {
    val paper = MaterialTheme.colorScheme.tertiaryContainer
    val ink = MaterialTheme.colorScheme.onTertiaryContainer
    val tape = MaterialTheme.colorScheme.tertiary

    Column(
        modifier = modifier
            .fillMaxWidth()
            .rotate(-0.6f)
            .shadow(1.dp, labelShape)
            .clip(labelShape)
            .background(paper)
            .border(1.dp, labelEdge, labelShape)
            .drawBehind {
                val period = 10.dp.toPx()
                val tapeBrush = Brush.linearGradient(
                    0f to tape,
                    0.5f to tape,
                    0.5f to Color.White,
                    1f to Color.White,
                    start = Offset.Zero,
                    end = Offset(period, -period),
                    tileMode = TileMode.Repeated,
                )
                drawRect(tapeBrush, size = Size(size.width, 8.dp.toPx()))

                val inset = 4.dp.toPx()
                val top = 12.dp.toPx()
                drawRoundRect(
                    color = labelStitch,
                    topLeft = Offset(inset, top),
                    size = Size(size.width - inset * 2, size.height - top - inset),
                    cornerRadius = CornerRadius(3.dp.toPx()),
                    style = Stroke(
                        width = 1.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx())),
                    ),
                )
            }
            .padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Eyebrow(eyebrow, color = ink)
        Text(
            title,
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
            color = ink,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        rows.forEach { (cause, source) ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(cause, style = MaterialTheme.typography.bodyMedium, color = ink, modifier = Modifier.weight(1f))
                Text(
                    source,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = DmMono),
                    color = labelInkMuted,
                )
            }
        }
    }
}
