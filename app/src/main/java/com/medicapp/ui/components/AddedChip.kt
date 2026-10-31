package com.medicapp.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Sello discreto para lo que el usuario ya tiene añadido: historial, recordatorios, alergias. Va
 * en gris, no en color: es información, no pide atención.
 */
@Composable
fun AddedChip(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Default.Check,
) {
    AssistChip(
        onClick = {},
        enabled = false,
        label = { Text(text) },
        leadingIcon = { Icon(icon, contentDescription = null) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
            disabledLeadingIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        modifier = modifier,
    )
}
