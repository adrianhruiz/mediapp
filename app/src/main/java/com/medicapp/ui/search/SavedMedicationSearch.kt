package com.medicapp.ui.search

import com.medicapp.model.CustomMedication
import com.medicapp.model.Medication
import com.medicapp.model.SavedMedication
import java.text.Normalizer

/** Qué se abre al tocar un medicamento guardado en la búsqueda. */
sealed interface SavedSelection {
    /** Un medicamento de CIMA: su ficha. */
    data class Detail(val medication: Medication) : SavedSelection

    /** Un medicamento propio: CIMA no tiene ficha, así que se abre su diálogo con el nombre. */
    data class Custom(val nombre: String) : SavedSelection
}

/**
 * Búsqueda entre los medicamentos que el usuario ya tiene.
 *
 * Se filtra en el móvil porque la lista ya está aquí (un documento de resumen y las pautas
 * activas) y Firestore no sabe buscar sin acentos ni por palabras sueltas.
 */
object SavedMedicationSearch {

    /**
     * Los que contienen todas las palabras de [query], en cualquier orden, sin distinguir acentos
     * ni mayúsculas. Una consulta en blanco no devuelve nada, igual que la búsqueda en CIMA.
     */
    fun filter(saved: List<SavedMedication>, query: String): List<SavedMedication> {
        val words = normalize(query).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return emptyList()
        return saved.filter { medication ->
            val nombre = normalize(medication.nombre)
            words.all { it in nombre }
        }
    }

    fun selectionFor(saved: SavedMedication): SavedSelection =
        if (CustomMedication.isCustom(saved.nregistro)) {
            SavedSelection.Custom(saved.nombre)
        } else {
            SavedSelection.Detail(saved.toMedication())
        }

    /** Para pintarlo con la misma tarjeta que los resultados de CIMA. */
    fun SavedMedication.toMedication() = Medication(
        nregistro = nregistro,
        nombre = nombre,
        labtitular = null,
        dosis = null,
        comercializado = null,
        requiereReceta = null,
        fotoUrl = fotoUrl,
    )

    private fun normalize(text: String): String = Normalizer.normalize(text, Normalizer.Form.NFD)
        .replace(Regex("\\p{M}"), "")
        .lowercase()
}
