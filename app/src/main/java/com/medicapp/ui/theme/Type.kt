package com.medicapp.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import com.medicapp.R

// Las fuentes van dentro del APK y no como fuentes descargables: así se ven igual sin red y
// sin depender de los servicios de Google del móvil.

val Bricolage = FontFamily(
    Font(R.font.bricolage_regular, FontWeight.Normal),
    Font(R.font.bricolage_medium, FontWeight.Medium),
    Font(R.font.bricolage_semibold, FontWeight.SemiBold),
    Font(R.font.bricolage_bold, FontWeight.Bold),
)

/** Para datos que se comparan de un vistazo: dosis, horas, recuentos. */
val DmMono = FontFamily(
    Font(R.font.dm_mono_regular, FontWeight.Normal),
    Font(R.font.dm_mono_medium, FontWeight.Medium),
)

private val Base = Typography()

private fun TextStyle.blister(weight: FontWeight, tracking: Float = 0f) =
    copy(fontFamily = Bricolage, fontWeight = weight, letterSpacing = tracking.em)

// Se conservan los tamaños de Material y solo cambian familia, peso y espaciado: los títulos
// se aprietan un poco porque Bricolage a cuerpo grande respira demasiado.
val Typography = Typography(
    displayLarge = Base.displayLarge.blister(FontWeight.Bold, -0.03f),
    displayMedium = Base.displayMedium.blister(FontWeight.Bold, -0.03f),
    displaySmall = Base.displaySmall.blister(FontWeight.Bold, -0.03f),
    headlineLarge = Base.headlineLarge.blister(FontWeight.Bold, -0.02f),
    headlineMedium = Base.headlineMedium.blister(FontWeight.Bold, -0.02f),
    headlineSmall = Base.headlineSmall.blister(FontWeight.Bold, -0.02f),
    titleLarge = Base.titleLarge.blister(FontWeight.SemiBold, -0.01f),
    titleMedium = Base.titleMedium.blister(FontWeight.SemiBold),
    titleSmall = Base.titleSmall.blister(FontWeight.SemiBold),
    bodyLarge = Base.bodyLarge.blister(FontWeight.Normal),
    bodyMedium = Base.bodyMedium.blister(FontWeight.Normal),
    bodySmall = Base.bodySmall.blister(FontWeight.Normal),
    labelLarge = Base.labelLarge.blister(FontWeight.SemiBold),
    labelMedium = Base.labelMedium.blister(FontWeight.Medium),
    labelSmall = Base.labelSmall.blister(FontWeight.Medium),
)
