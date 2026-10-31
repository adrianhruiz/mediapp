package com.medicapp.model

import java.text.Normalizer

/**
 * Medicamentos que no están en CIMA (una pomada, un remedio sin registro...) y que el usuario
 * escribe a mano.
 *
 * No tienen colección propia: el historial y los recordatorios ya guardan el número de registro
 * y el nombre como texto, así que basta con darles un número de registro inventado. Sale del
 * propio nombre para que escribir dos veces "Pomada de árnica" sea el mismo medicamento: así
 * siguen funcionando el aviso de duplicados, el resumen del historial y los distintivos.
 *
 * El prefijo los distingue de los de CIMA, que son solo dígitos, para no pedir su ficha.
 */
object CustomMedication {
    private const val PREFIX = "propio-"

    /**
     * El número de registro inventado, o `null` si el nombre no tiene letras ni números. Sin
     * acentos, en minúscula y con guiones: ni puntos ni barras, que Firestore lee como rutas.
     */
    fun idFor(nombre: String): String? {
        val slug = Normalizer.normalize(nombre, Normalizer.Form.NFD)
            .replace(Regex("\\p{M}"), "")
            .lowercase()
            .replace(Regex("[^a-z0-9]+"), "-")
            .trim('-')
        return slug.takeIf { it.isNotEmpty() }?.let { PREFIX + it }
    }

    fun isCustom(nregistro: String): Boolean = nregistro.startsWith(PREFIX)
}
