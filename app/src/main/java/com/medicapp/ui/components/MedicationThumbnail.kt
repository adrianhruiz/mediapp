package com.medicapp.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage

/**
 * Foto de la caja según CIMA. Si no hay foto se dibuja una cajita del color de su pastilla
 * ([seed] es el número de registro), para que cada medicamento se reconozca igual de un vistazo.
 */
@Composable
fun MedicationThumbnail(fotoUrl: String?, modifier: Modifier = Modifier, seed: String? = null) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .size(56.dp)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (fotoUrl != null) {
            AsyncImage(
                model = fotoUrl,
                contentDescription = "Foto del medicamento",
                modifier = Modifier.size(56.dp).clip(shape),
                contentScale = ContentScale.Crop,
            )
        } else {
            val packColor = seed?.let(PillStyles::packColor) ?: MaterialTheme.colorScheme.outline
            Canvas(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
                val corner = CornerRadius(4.dp.toPx())
                drawRoundRect(packColor, size = size, cornerRadius = corner)
                // La solapa de la caja, un poco más clara.
                drawRoundRect(
                    color = Color.White.copy(alpha = 0.45f),
                    topLeft = Offset.Zero,
                    size = Size(size.width, size.height * 0.24f),
                    cornerRadius = corner,
                )
            }
        }
    }
}
