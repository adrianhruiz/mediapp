package com.medicapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

/** Qué botón de gel: perla para casi todo, coral solo para la acción que pide atención. */
enum class GelTone { PEARL, CORAL }

// Brillo de botón de 2010: la mitad de arriba más clara y un corte limpio a media altura.
private val pearl = Brush.verticalGradient(
    0f to Color(0xFFFFFFFF),
    0.5f to Color(0xFFF1F2F4),
    0.5f to Color(0xFFE1E4E8),
    1f to Color(0xFFECEEF1),
)
private val pearlPressed = Brush.verticalGradient(listOf(Color(0xFFC9CED5), Color(0xFFDDE1E6)))
private val coral = Brush.verticalGradient(
    0f to Color(0xFFF07A70),
    0.5f to Color(0xFFDD4C42),
    0.5f to Color(0xFFCC3E35),
    1f to Color(0xFFD9493F),
)
private val coralPressed = Brush.verticalGradient(listOf(Color(0xFFB8362E), Color(0xFFCC4339)))
private val pearlEdge = Color(0xFFA9B0BA)
private val coralEdge = Color(0xFFA9332B)
private val ink = Color(0xFF1E2531)

/** Texto grabado: una línea blanca debajo, como si estuviera estampado en el plástico. */
val EngravedShadow = Shadow(color = Color.White, offset = Offset(0f, 1.5f), blurRadius = 0f)
private val CoralShadow = Shadow(color = Color(0x73601410), offset = Offset(0f, -1.5f), blurRadius = 0f)

/**
 * Botón de gel con relieve. Al pulsarlo se hunde: pierde la sombra y el brillo se invierte.
 * Desactivado se queda igual de forma pero medio transparente, para que se lea qué hará.
 */
@Composable
fun GelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tone: GelTone = GelTone.PEARL,
    enabled: Boolean = true,
    shape: Shape = CircleShape,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    content: @Composable RowScope.() -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val isCoral = tone == GelTone.CORAL
    val fill = when {
        pressed && isCoral -> coralPressed
        pressed -> pearlPressed
        isCoral -> coral
        else -> pearl
    }
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.45f)
            .defaultMinSize(minHeight = 48.dp)
            .shadow(if (pressed || !enabled) 0.dp else 2.dp, shape)
            .clip(shape)
            .background(fill)
            .border(1.dp, if (isCoral) coralEdge else pearlEdge, shape)
            .clickable(
                interactionSource = interaction,
                indication = null,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(contentPadding),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CompositionLocalProvider(LocalContentColor provides if (isCoral) Color.White else ink) {
            ProvideTextStyle(
                MaterialTheme.typography.labelLarge.copy(shadow = if (isCoral) CoralShadow else EngravedShadow),
            ) {
                content()
            }
        }
    }
}

/** Botón pequeño de cabecera, como «Editar» o «Guardar» en una barra de navegación de 2010. */
@Composable
fun GelBarButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    GelButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
        content = content,
    )
}
