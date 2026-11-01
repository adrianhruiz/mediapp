package com.medicapp.domain

import com.medicapp.data.repository.AllergyRepository
import com.medicapp.data.repository.AtcEnglishNameSource
import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Pone a las familias su nombre oficial en inglés en cuanto aparecen, y lo deja guardado para que
 * el informe funcione sin conexión en el extranjero. Los principios activos no se traducen: de
 * ellos no hay una equivalencia exacta, y el usuario puede escribir el nombre a mano.
 */
class AllergyTranslator(
    private val allergyRepository: AllergyRepository,
    private val englishNames: AtcEnglishNameSource,
) {
    /** Códigos ya consultados en esta ejecución, para no repetir una consulta sin respuesta. */
    private val requested = mutableSetOf<String>()

    fun start(scope: CoroutineScope) {
        scope.launch {
            allergyRepository.getAll().collect { allergies ->
                allergies.filter(::needsTranslation).forEach { translate(it) }
            }
        }
    }

    private fun needsTranslation(allergy: Allergy) =
        allergy.tipo == AllergyType.FAMILIA && allergy.nombreIngles == null && allergy.atcCodigo != null

    private suspend fun translate(allergy: Allergy) {
        val code = allergy.atcCodigo ?: return
        if (!requested.add(code)) return
        val name = englishNames.englishName(code) ?: return
        runCatching { allergyRepository.setEnglishName(allergy.id, name, manual = false) }
    }
}
