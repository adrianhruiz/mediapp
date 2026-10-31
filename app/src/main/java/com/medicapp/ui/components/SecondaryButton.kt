package com.medicapp.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Botón secundario: gel perla, igual que el principal cuando este no pide atención. La
 * jerarquía la dan el sitio y el coral, no un contorno negro que pesaba más que el contenido.
 */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    GelButton(onClick = onClick, modifier = modifier, enabled = enabled, content = content)
}
