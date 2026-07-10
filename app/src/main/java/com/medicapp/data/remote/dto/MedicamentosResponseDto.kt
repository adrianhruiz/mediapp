package com.medicapp.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class MedicamentosResponseDto(
    val totalFilas: Int,
    val pagina: Int,
    val tamanioPagina: Int,
    val resultados: List<MedicamentoDto>,
)
