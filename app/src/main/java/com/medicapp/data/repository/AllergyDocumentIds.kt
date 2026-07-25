package com.medicapp.data.repository

import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType

/**
 * La id del documento se calcula a partir de la propia alergia, no la sortea Firestore.
 *
 * Así, guardar dos veces la misma alergia escribe dos veces el mismo documento en vez de crear
 * uno nuevo: el duplicado deja de ser algo que hay que evitar preguntando antes de escribir y
 * pasa a ser imposible por construcción. Eso nos ahorra una consulta por cada alta —y en
 * "Añadir todas" eran tantas consultas como resultados.
 *
 * El prefijo separa los dos tipos de alergia para que un principio activo y una familia no
 * puedan chocar nunca. El código ATC se normaliza a mayúsculas porque CIMA lo devuelve así y
 * queremos que la misma familia dé siempre la misma id.
 */
fun principioActivoDocumentId(principioActivoId: Int): String = "pa-$principioActivoId"

fun familiaDocumentId(atcCodigo: String): String = "atc-${atcCodigo.trim().uppercase()}"

/** `null` si a la alergia le falta el dato del que sale su id, y por tanto no la tocamos. */
fun deterministicAllergyId(allergy: Allergy): String? = when (allergy.tipo) {
    AllergyType.PRINCIPIO_ACTIVO -> allergy.principioActivoId?.let { principioActivoDocumentId(it) }
    AllergyType.FAMILIA -> allergy.atcCodigo?.takeIf { it.isNotBlank() }?.let { familiaDocumentId(it) }
}

/**
 * Qué alergias guardadas antes de este cambio hay que reescribir, como `id vieja → id nueva`.
 *
 * Sin esto, una alergia dada de alta con la id sorteada seguiría siendo invisible para la
 * escritura determinista y volvería a darse de alta duplicada. Como dos documentos antiguos de
 * la misma alergia caen en la misma id nueva, la migración además los funde en uno.
 */
fun planAllergyIdMigration(allergies: List<Allergy>): Map<String, String> = allergies
    .mapNotNull { allergy ->
        deterministicAllergyId(allergy)
            ?.takeIf { it != allergy.id }
            ?.let { allergy.id to it }
    }
    .toMap()
