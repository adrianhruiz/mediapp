package com.medicapp.ui.search

import com.medicapp.ui.components.MedicationNames

/** Una cantidad con su unidad: «10 mg», «20 mg/ml», «1,5 g». */
private val amountPattern = Regex("""(\d+(?:[.,]\d+)?)\s*([a-zµ]+(?:/[a-z]+)?)""")

/** Las cantidades de un texto, normalizadas para compararlas («10 MG» y «10 mg» son la misma). */
private fun amounts(text: String): Set<String> =
    amountPattern.findAll(text.lowercase()).map { "${it.groupValues[1].replace(',', '.')} ${it.groupValues[2]}" }.toSet()

/**
 * Nombre comercial y, aparte, «dosis · forma». La dosis de CIMA suele repetir la que ya va en el
 * nombre; solo se añade cuando aporta algo.
 *
 * Se comparan las cantidades y no el texto entero: CIMA a veces añade el principio activo
 * («10 mg ebastina») y entonces la frase no aparecía tal cual en el nombre aunque la cantidad
 * fuera la misma, y salía «10 mg ebastina · 10 mg comprimidos».
 */
internal fun nameAndPresentation(nombre: String, dosis: String?): Pair<String, String?> {
    val (name, detail) = MedicationNames.split(nombre)
    val extraDose = dosis
        ?.takeIf { it.isNotBlank() && !alreadyIn(it, detail) }
        ?.let(MedicationNames::detail)
    val presentation = listOfNotNull(extraDose, detail).joinToString(" · ").ifBlank { null }
    return name to presentation
}

private fun alreadyIn(dosis: String, detail: String?): Boolean {
    if (detail == null) return false
    if (detail.contains(dosis, ignoreCase = true)) return true
    val doseAmounts = amounts(dosis)
    return doseAmounts.isNotEmpty() && amounts(detail).containsAll(doseAmounts)
}
