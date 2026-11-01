package com.medicapp.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Una fila de la clasificación ATC de ChEMBL: los cinco niveles de un código con su nombre OMS en inglés. */
@Serializable
data class ChemblAtcClassDto(
    val level1: String? = null,
    @SerialName("level1_description") val level1Description: String? = null,
    val level2: String? = null,
    @SerialName("level2_description") val level2Description: String? = null,
    val level3: String? = null,
    @SerialName("level3_description") val level3Description: String? = null,
    val level4: String? = null,
    @SerialName("level4_description") val level4Description: String? = null,
    val level5: String? = null,
    @SerialName("who_name") val whoName: String? = null,
)

@Serializable
data class ChemblAtcListDto(val atc: List<ChemblAtcClassDto> = emptyList())
