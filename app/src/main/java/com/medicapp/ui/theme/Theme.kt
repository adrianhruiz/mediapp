package com.medicapp.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable

// Sin color dinámico: con él, el acento coral dependía del fondo de pantalla del móvil y el
// aviso de alergia o la toma pendiente dejaban de verse igual de un teléfono a otro.
@Composable
fun MedicAppTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) BlisterDarkColors else BlisterLightColors,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
