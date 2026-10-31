package com.medicapp.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable

// Sin color dinámico: con él, el acento coral dependía del fondo de pantalla del móvil y el
// aviso de alergia o la toma pendiente dejaban de verse igual de un teléfono a otro.
//
// Y siempre en claro, aunque el móvil esté en modo oscuro: la app imita un pastillero de
// plástico blanco y aluminio, y en oscuro dejaba de parecerlo y se sentía pesada.
@Composable
fun MedicAppTheme(content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalBlisterColors provides LightBlisterColors) {
        MaterialTheme(
            colorScheme = BlisterLightColors,
            typography = Typography,
            shapes = Shapes,
            content = content,
        )
    }
}

object BlisterTheme {
    val colors: BlisterColors
        @Composable
        @ReadOnlyComposable
        get() = LocalBlisterColors.current
}
