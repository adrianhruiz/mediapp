package com.medicapp.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.medicapp.ui.theme.DmMono
import java.util.Locale

private val spanish = Locale.forLanguageTag("es-ES")

/** Línea corta en mayúsculas y DM Mono, grabada, encima de un título o una sección. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(
        text = text.uppercase(spanish),
        style = MaterialTheme.typography.labelMedium.copy(
            fontFamily = DmMono,
            letterSpacing = 0.08.em,
            shadow = EngravedShadow,
        ),
        color = color,
        modifier = modifier,
    )
}

/**
 * Cabecera de pantalla del blíster: antetítulo pequeño, título grande y, si hace falta, volver y
 * acciones a la derecha. Sustituye a la barra superior de Material, que quedaba plana.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
            }
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            eyebrow?.let { Eyebrow(it) }
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium.copy(shadow = EngravedShadow),
                modifier = Modifier.semantics { heading() },
            )
        }
        actions()
    }
}
