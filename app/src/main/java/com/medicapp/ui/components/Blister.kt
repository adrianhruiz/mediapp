package com.medicapp.ui.components

import android.content.res.Configuration
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.medicapp.ui.theme.BlisterColors
import com.medicapp.ui.theme.BlisterTheme
import com.medicapp.ui.theme.MedicAppTheme

/** Lo que enseña una celda: la pastilla dentro, la lámina rota o la pastilla que se saltó. */
enum class DoseCellState { PENDING, TAKEN, SKIPPED }

// Proporción de una celda de blíster de cápsula: algo más alta que ancha.
private const val CELL_ASPECT = 1.48f
private val RING_GAP = 3.dp
private val RING_WIDTH = 3.dp
private val RING_SPACE = RING_GAP + RING_WIDTH

// Contorno de la lámina arrancada, en fracciones de su caja.
private val flapOutline = listOf(
    0f to 1f, 0.08f to 0.3f, 0.4f to 0f, 0.7f to 0.2f, 1f to 0.9f, 0.6f to 0.6f, 0.3f to 0.8f,
)

/**
 * Una celda del blíster. Al pasar a [DoseCellState.TAKEN] la lámina se rompe con una animación
 * corta, que Compose acorta o anula si el sistema tiene las animaciones reducidas.
 *
 * [highlighted] la rodea de coral: es la toma que pide atención (la que toca o la que se escapó).
 * El dibujo es decorativo; quien la use debe dar la descripción accesible en [modifier].
 */
@Composable
fun BlisterCell(
    state: DoseCellState,
    pill: PillStyle,
    modifier: Modifier = Modifier,
    width: Dp = 70.dp,
    highlighted: Boolean = false,
) {
    val colors = BlisterTheme.colors
    val ringColor = MaterialTheme.colorScheme.tertiary
    val tear = remember { Animatable(if (state == DoseCellState.TAKEN) 1f else 0f) }
    LaunchedEffect(state) {
        val target = if (state == DoseCellState.TAKEN) 1f else 0f
        if (tear.value != target) tear.animateTo(target, tween(420, easing = FastOutSlowInEasing))
    }

    Canvas(modifier.size(width + RING_SPACE * 2, width * CELL_ASPECT + RING_SPACE * 2)) {
        val pad = RING_SPACE.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val t = tear.value
        translate(pad, pad) {
            if (highlighted) drawRing(ringColor, w, h)
            drawWell(colors, w, h)
            val pillAlpha = (1f - t) * if (state == DoseCellState.SKIPPED) 0.3f else 1f
            if (pillAlpha > 0f) {
                scale(1f - 0.3f * t, pivot = Offset(w / 2, h / 2)) {
                    drawPill(pill, colors, w, h, pillAlpha)
                }
            }
            if (t > 0f) drawTorn(colors, w, h, t)
        }
    }
}

/** Fondo de aluminio para agrupar celdas, como la lámina de un blíster de verdad. */
@Composable
fun FoilCard(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 14.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = BlisterTheme.colors
    val shape = MaterialTheme.shapes.large
    Column(
        modifier = modifier
            .shadow(6.dp, shape)
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.foilLight, colors.foilDark)))
            .padding(contentPadding),
        content = content,
    )
}

private fun DrawScope.drawRing(color: Color, w: Float, h: Float) {
    val stroke = RING_WIDTH.toPx()
    val inset = RING_GAP.toPx() + stroke / 2
    drawRoundRect(
        color = color,
        topLeft = Offset(-inset, -inset),
        size = Size(w + inset * 2, h + inset * 2),
        cornerRadius = CornerRadius(w / 2 + inset),
        style = Stroke(stroke),
    )
}

private fun DrawScope.drawWell(colors: BlisterColors, w: Float, h: Float) {
    val corner = CornerRadius(w / 2)
    drawRoundRect(
        brush = Brush.radialGradient(
            colors = listOf(colors.glass, colors.glass.copy(alpha = colors.glass.alpha * 0.4f), colors.glassEdge),
            center = Offset(w * 0.35f, h * 0.25f),
            radius = h * 0.85f,
        ),
        size = Size(w, h),
        cornerRadius = corner,
    )
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(Color.Transparent, colors.wellShade), startY = h * 0.55f, endY = h),
        size = Size(w, h),
        cornerRadius = corner,
    )
}

private fun DrawScope.drawPill(pill: PillStyle, colors: BlisterColors, w: Float, h: Float, alpha: Float) {
    val center = Offset(w / 2, h / 2)
    val drop = Offset(0f, 2.dp.toPx())
    when (pill.shape) {
        PillShape.CAPSULE -> rotate(32f, pivot = center) {
            val cw = w * 0.34f
            val ch = h * 0.6f
            val topLeft = Offset(center.x - cw / 2, center.y - ch / 2)
            val pillSize = Size(cw, ch)
            val corner = CornerRadius(cw / 2)
            drawRoundRect(colors.pillShadow, topLeft + drop, pillSize, corner, alpha = alpha)
            drawRoundRect(pill.bottom, topLeft, pillSize, corner, alpha = alpha)
            clipRect(left = topLeft.x, top = topLeft.y, right = topLeft.x + cw, bottom = center.y) {
                drawRoundRect(pill.top, topLeft, pillSize, corner, alpha = alpha)
            }
            drawRoundRect(colors.pillShadow, topLeft, pillSize, corner, alpha = alpha * 0.5f, style = Stroke(0.75.dp.toPx()))
            drawLine(
                color = Color.White.copy(alpha = 0.45f * alpha),
                start = Offset(topLeft.x + cw * 0.3f, topLeft.y + ch * 0.15f),
                end = Offset(topLeft.x + cw * 0.3f, topLeft.y + ch * 0.4f),
                strokeWidth = cw * 0.14f,
                cap = StrokeCap.Round,
            )
        }
        PillShape.TABLET -> {
            val r = w * 0.3f
            drawCircle(colors.pillShadow, r, center + drop, alpha = alpha)
            drawCircle(colors.tablet, r, center, alpha = alpha)
            drawCircle(
                brush = Brush.verticalGradient(listOf(Color.Transparent, colors.tabletShade), startY = center.y, endY = center.y + r),
                radius = r,
                center = center,
                alpha = alpha,
            )
            drawLine(
                color = colors.tabletShade,
                start = Offset(center.x - r * 0.6f, center.y),
                end = Offset(center.x + r * 0.6f, center.y),
                strokeWidth = 1.5.dp.toPx(),
                alpha = alpha,
            )
        }
    }
}

private fun DrawScope.drawTorn(colors: BlisterColors, w: Float, h: Float, t: Float) {
    val corner = CornerRadius(w / 2)
    drawRoundRect(colors.hole, size = Size(w, h), cornerRadius = corner, alpha = t)
    drawRoundRect(
        brush = Brush.verticalGradient(listOf(colors.holeShade, Color.Transparent), startY = 0f, endY = h * 0.35f),
        size = Size(w, h),
        cornerRadius = corner,
        alpha = t,
    )
    // La lámina sale doblada desde el borde de arriba: crece desde ahí mientras se rompe.
    val boxLeft = w * 0.11f
    val boxTop = -h * 0.04f
    val boxWidth = w * 0.77f
    val boxHeight = h * 0.29f
    val flap = Path().apply {
        flapOutline.forEachIndexed { index, (fx, fy) ->
            val x = boxLeft + fx * boxWidth
            val y = boxTop + fy * boxHeight
            if (index == 0) moveTo(x, y) else lineTo(x, y)
        }
        close()
    }
    val pivot = Offset(boxLeft + boxWidth / 2, boxTop)
    scale(t, pivot = pivot) {
        rotate(-8f, pivot = pivot) {
            drawPath(
                path = flap,
                brush = Brush.linearGradient(
                    listOf(colors.flapLight, colors.flapDark),
                    start = Offset(boxLeft, boxTop),
                    end = Offset(boxLeft + boxWidth, boxTop + boxHeight),
                ),
            )
        }
    }
}

@Preview(name = "Claro")
@Preview(name = "Oscuro", uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun BlisterCellPreview() {
    MedicAppTheme {
        Surface {
            FoilCard(modifier = Modifier.padding(16.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val capsule = PillStyles.forMedication("62411", "Omeprazol cápsulas duras")
                    val tablet = PillStyles.forMedication("70300", "Atorvastatina comprimidos")
                    BlisterCell(DoseCellState.TAKEN, capsule)
                    BlisterCell(DoseCellState.PENDING, capsule, highlighted = true)
                    BlisterCell(DoseCellState.PENDING, tablet)
                    BlisterCell(DoseCellState.SKIPPED, tablet)
                }
            }
        }
    }
}
