package com.medicapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MedicamentoDetailDto(
    val nregistro: String,
    val nombre: String,
    val labtitular: String? = null,
    val dosis: String? = null,
    val comerc: Boolean? = null,
    val receta: Boolean? = null,
    val fotos: List<FotoDto>? = null,
    val principiosActivos: List<PrincipioActivoDto>? = null,
    val atcs: List<AtcDto>? = null,
)

@Serializable
data class PrincipioActivoDto(
    val id: Int,
    val codigo: String? = null,
    val nombre: String,
    val cantidad: String? = null,
    val unidad: String? = null,
    val orden: Int? = null,
)

@Serializable
data class AtcDto(
    val codigo: String,
    val nombre: String,
    val nivel: Int? = null,
)
