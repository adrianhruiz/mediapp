package com.medicapp.ui.theme

import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color

// Paleta "Blíster": aluminio de fondo, tinta para el texto y los botones, y un único acento
// coral reservado para lo que pide atención (la toma que toca, la que se escapó, una alergia).

private val Ink = Color(0xFF18202C)
private val InkMuted = Color(0xFF6A7482)
private val InkSoft = Color(0xFF3C4655)
private val Foil = Color(0xFFF3F4F6)
private val FoilDeep = Color(0xFFE4E7EC)
private val FoilLine = Color(0xFFC2C8D0)
private val Paper = Color(0xFFFFFFFF)
private val Coral = Color(0xFFFF5A4F)
private val CoralSoft = Color(0xFFFFE1DE)
private val CoralInk = Color(0xFF7A1A12)
private val AlertRed = Color(0xFFB5271C)


// Las tarjetas van en blanco sobre el aluminio, así que todos los contenedores de superficie
// son papel: así se separan del fondo sin depender de la elevación tonal de Material.
val BlisterLightColors = lightColorScheme(
    primary = Ink,
    onPrimary = Paper,
    primaryContainer = FoilDeep,
    onPrimaryContainer = Ink,
    secondary = InkSoft,
    onSecondary = Paper,
    secondaryContainer = FoilDeep,
    onSecondaryContainer = Ink,
    tertiary = Coral,
    onTertiary = Ink,
    tertiaryContainer = CoralSoft,
    onTertiaryContainer = CoralInk,
    error = AlertRed,
    onError = Paper,
    errorContainer = CoralSoft,
    onErrorContainer = CoralInk,
    background = Foil,
    onBackground = Ink,
    surface = Foil,
    onSurface = Ink,
    surfaceVariant = FoilDeep,
    onSurfaceVariant = InkMuted,
    surfaceContainerLowest = Paper,
    surfaceContainerLow = Paper,
    surfaceContainer = Paper,
    surfaceContainerHigh = Paper,
    surfaceContainerHighest = Paper,
    outline = FoilLine,
    outlineVariant = FoilDeep,
    inverseSurface = Ink,
    inverseOnSurface = Foil,
    inversePrimary = FoilDeep,
)
