package com.medicapp.domain

import com.medicapp.model.Allergy
import com.medicapp.model.AllergyMatch
import com.medicapp.model.AllergyType
import com.medicapp.model.MedicationDetail

object AllergyChecker {

    fun findMatches(medication: MedicationDetail, allergies: List<Allergy>): List<AllergyMatch> {
        val ingredientIds = medication.principiosActivos.map { it.id }.toSet()
        val atcCodes = medication.atcs.map { it.codigo }

        return allergies.mapNotNull { allergy ->
            when (allergy.tipo) {
                AllergyType.PRINCIPIO_ACTIVO -> {
                    if (allergy.principioActivoId != null && allergy.principioActivoId in ingredientIds) {
                        AllergyMatch(
                            allergy = allergy,
                            descripcion = "Alergia al componente activo ${allergy.principioActivoNombre.orEmpty()}",
                        )
                    } else {
                        null
                    }
                }
                AllergyType.FAMILIA -> {
                    val atcCodigo = allergy.atcCodigo
                    if (atcCodigo != null && atcCodes.any { it.startsWith(atcCodigo) }) {
                        AllergyMatch(
                            allergy = allergy,
                            descripcion = "Alergia a la familia ${allergy.atcNombre.orEmpty()}",
                        )
                    } else {
                        null
                    }
                }
            }
        }
    }
}
