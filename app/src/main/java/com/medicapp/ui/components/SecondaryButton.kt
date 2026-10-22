package com.medicapp.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Botón de contorno en tinta. El de Material pinta texto gris y un borde casi invisible, y sobre
 * el aluminio parecía desactivado.
 */
@Composable
fun SecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    val ink = MaterialTheme.colorScheme.onSurface
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = ink),
        border = BorderStroke(1.5.dp, if (enabled) ink else MaterialTheme.colorScheme.outline),
        content = content,
    )
}
