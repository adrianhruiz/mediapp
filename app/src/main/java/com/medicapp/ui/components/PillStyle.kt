package com.medicapp.ui.components

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import java.text.Normalizer

enum class PillShape { CAPSULE, TABLET }

/** Cómo se pinta la pastilla de un medicamento dentro de su celda del blíster. */
@Immutable
data class PillStyle(val shape: PillShape, val top: Color, val bottom: Color)

object PillStyles {

    // Ninguna lleva coral: ese color está reservado para lo que pide atención, y una cápsula
    // coral se confundiría con el aviso de "te toca".
    private val palette = listOf(
        Color(0xFF2F6FE4) to Color(0xFFF5C84B),
        Color(0xFF2A9D8F) to Color(0xFFFFFFFF),
        Color(0xFF7B5EA7) to Color(0xFFFFFFFF),
        Color(0xFFD9A441) to Color(0xFFFFFFFF),
        Color(0xFF3C4655) to Color(0xFFE4E7EC),
    )

    private val tabletWords = listOf("COMPRIMIDO", "GRAGEA", "PASTILLA", "TABLETA")

    /**
     * La forma sale del nombre de CIMA, que suele incluir la presentación ("cápsulas duras",
     * "comprimidos recubiertos"). Si no la dice (sobres, jarabes), se pinta cápsula. El color se
     * deriva del número de registro, así que cada medicamento conserva siempre el suyo.
     */
    fun forMedication(nregistro: String, nombre: String): PillStyle {
        val plain = Normalizer.normalize(nombre.uppercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
        val shape = if (tabletWords.any { it in plain }) PillShape.TABLET else PillShape.CAPSULE
        val (top, bottom) = palette[Math.floorMod(nregistro.hashCode(), palette.size)]
        return PillStyle(shape, top, bottom)
    }

    /** Color de la caja cuando CIMA no tiene foto: el mismo tono que su pastilla. */
    fun packColor(nregistro: String): Color = palette[Math.floorMod(nregistro.hashCode(), palette.size)].first
}
