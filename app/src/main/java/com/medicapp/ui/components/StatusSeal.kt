package com.medicapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Botoncito de perla en relieve, como la pegatina de un pastillero.
private val sealFace = Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFFDFE3E8)))
private val sealEdge = Color(0xFFB4BBC5)
private val sealInk = Color(0xFF1E2531)

/**
 * Sello de estado que se pega en la esquina de [MedicationThumbnail]. Solo lleva icono, así que
 * [description] es lo que lee TalkBack: sin ella el estado no existiría para quien no ve la foto.
 */
@Composable
fun StatusSeal(icon: ImageVector, description: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(20.dp)
            .shadow(1.5.dp, CircleShape)
            .background(sealFace, CircleShape)
            .border(1.dp, sealEdge, CircleShape)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = sealInk, modifier = Modifier.size(12.dp))
    }
}
