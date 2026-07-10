package com.medicapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MaestraItemDto(
    val id: Int? = null,
    val codigo: String? = null,
    val nombre: String,
    val nivel: Int? = null,
)

@Serializable
data class MaestrasResponseDto(
    val totalFilas: Int,
    val pagina: Int,
    val tamanioPagina: Int,
    val resultados: List<MaestraItemDto>,
)
