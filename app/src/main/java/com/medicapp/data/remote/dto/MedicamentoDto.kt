package com.medicapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MedicamentoDto(
    val nregistro: String,
    val nombre: String,
    val labtitular: String? = null,
    val dosis: String? = null,
    val comerc: Boolean? = null,
    val receta: Boolean? = null,
    val fotos: List<FotoDto>? = null,
)

@Serializable
data class FotoDto(
    val tipo: String? = null,
    val url: String? = null,
)
