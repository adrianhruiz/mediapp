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

// En oscuro el aluminio se apaga, pero las pastillas siguen claras: son objetos físicos y en la
// vida real no cambian de color con el modo noche.
val DarkBlisterColors = BlisterColors(
    foilLight = Color(0xFF3A4250),
    foilDark = Color(0xFF232A35),
    glass = Color(0x33FFFFFF),
    glassEdge = Color(0x40000000),
    wellShade = Color(0x66000000),
    hole = Color(0xFF0B0E13),
    holeShade = Color(0xFF000000),
    flapLight = Color(0xFF5A6474),
    flapDark = Color(0xFF343C49),
    tablet = Color(0xFFE9ECF0),
    tabletShade = Color(0xFFB9C0CA),
    pillShadow = Color(0x80000000),
)

val LocalBlisterColors = staticCompositionLocalOf { LightBlisterColors }
