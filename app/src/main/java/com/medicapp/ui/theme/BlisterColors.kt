package com.medicapp.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Colores del blíster que no tienen hueco en el esquema de Material: el degradado del aluminio,
 * el plástico de las celdas y la lámina rota. Se leen con `BlisterTheme.colors`.
 */
@Immutable
data class BlisterColors(
    val foilLight: Color,
    val foilDark: Color,
    val glass: Color,
    val glassEdge: Color,
    val wellShade: Color,
    val hole: Color,
    val holeShade: Color,
    val flapLight: Color,
    val flapDark: Color,
    val tablet: Color,
    val tabletShade: Color,
    val pillShadow: Color,
)

val LightBlisterColors = BlisterColors(
    foilLight = Color(0xFFEDEFF3),
    foilDark = Color(0xFFBFC5CE),
    glass = Color(0xE6FFFFFF),
    glassEdge = Color(0x7396A0AF),
    wellShade = Color(0x2E18202C),
    hole = Color(0xFF7D8694),
    holeShade = Color(0xFF4E5562),
    flapLight = Color(0xFFF3F5F8),
    flapDark = Color(0xFFAEB5C0),
    tablet = Color(0xFFFFFFFF),
    tabletShade = Color(0xFFDDE1E7),
    pillShadow = Color(0x4D18202C),
)

val LocalBlisterColors = staticCompositionLocalOf { LightBlisterColors }
