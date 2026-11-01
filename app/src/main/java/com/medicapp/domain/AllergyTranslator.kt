package com.medicapp.domain

import com.medicapp.data.repository.AllergyRepository
import com.medicapp.data.repository.AtcEnglishNameSource
import com.medicapp.data.repository.AtcLookup
import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
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
    /** Códigos ya en marcha o resueltos en esta ejecución, para no repetir consultas. */
    private val requested = mutableSetOf<String>()

    fun start(scope: CoroutineScope) {
        scope.launch {
            allergyRepository.getAll().collect { allergies ->
                allergies.filter(::needsTranslation).forEach { allergy ->
                    val code = allergy.atcCodigo ?: return@forEach
                    // Cada código en su corrutina: la espera entre reintentos no frena a los demás.
                    if (requested.add(code)) scope.launch { translate(allergy.id, code) }
                }
            }
        }
    }

    private fun needsTranslation(allergy: Allergy) =
        allergy.tipo == AllergyType.FAMILIA && allergy.nombreIngles == null && allergy.atcCodigo != null

    /** Sin red se reintenta unas pocas veces, cada vez más espaciadas; sin equivalencia, nunca. */
    private suspend fun translate(allergyId: String, code: String) {
        repeat(MAX_ATTEMPTS) { attempt ->
            if (attempt > 0) delay(RETRY_DELAY_MS * attempt)
            when (val lookup = englishNames.englishName(code)) {
                is AtcLookup.Found -> {
                    try {
                        allergyRepository.setOfficialEnglishNameIfMissing(allergyId, lookup.name)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        // Se queda en español; al volver a abrir la app se intenta de nuevo.
                    }
                    return
                }
                AtcLookup.NotFound -> return
                AtcLookup.Failed -> Unit
            }
        }
    }

    companion object {
        const val MAX_ATTEMPTS = 3
        const val RETRY_DELAY_MS = 60_000L
    }
}
