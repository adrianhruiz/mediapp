package com.medicapp.domain

import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import java.text.Collator
import java.util.Locale

/**
 * Alergias separadas por tipo y por orden alfabético: la lista de la pestaña y el informe para
 * el extranjero las pintan bajo una cabecera por tipo en vez de repetir el tipo en cada fila.
 */
data class AllergyGroups(val families: List<Allergy>, val ingredients: List<Allergy>) {

    companion object {
        // El cotejo en español ordena sin distinguir mayúsculas ni tildes, como lo leería alguien.
        private val collator = Collator.getInstance(Locale.forLanguageTag("es-ES")).apply {
            strength = Collator.PRIMARY
        }

        fun of(allergies: List<Allergy>): AllergyGroups {
            val (families, ingredients) = allergies.partition { it.tipo == AllergyType.FAMILIA }
            return AllergyGroups(
                families = families.sortedWith(compareBy(collator) { it.atcNombre.orEmpty() }),
                ingredients = ingredients.sortedWith(compareBy(collator) { it.principioActivoNombre.orEmpty() }),
            )
        }
    }
}
