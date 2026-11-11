package com.medicapp.model

/**
 * Un medicamento que el usuario ya tiene: en el historial o con una pauta activa.
 *
 * Lleva nombre y foto para poder enseñarlo en la búsqueda sin pedir nada a CIMA, que es lo que
 * hace falta para los medicamentos propios: CIMA no los conoce y, si la búsqueda solo preguntaba
 * allí, no volvían a salir.
 */
data class SavedMedication(
    val nregistro: String,
    val nombre: String,
    val fotoUrl: String?,
)
