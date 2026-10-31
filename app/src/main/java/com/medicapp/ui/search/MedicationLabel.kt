package com.medicapp.ui.search

import com.medicapp.ui.components.MedicationNames

/**
 * Nombre comercial y, aparte, «dosis · forma». La dosis de CIMA suele repetir la que ya va en el
 * nombre; solo se añade cuando aporta algo.
 */
internal fun nameAndPresentation(nombre: String, dosis: String?): Pair<String, String?> {
    val (name, detail) = MedicationNames.split(nombre)
    val extraDose = dosis
        ?.takeIf { it.isNotBlank() && detail?.contains(it, ignoreCase = true) != true }
        ?.let(MedicationNames::detail)
    val presentation = listOfNotNull(extraDose, detail).joinToString(" · ").ifBlank { null }
    return name to presentation
}
