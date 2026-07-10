package com.medicapp.model

data class Allergy(
    val id: String,
    val tipo: AllergyType,
    val principioActivoId: Int? = null,
    val principioActivoNombre: String? = null,
    val atcCodigo: String? = null,
    val atcNombre: String? = null,
)
