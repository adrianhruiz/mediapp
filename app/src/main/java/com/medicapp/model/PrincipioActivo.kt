package com.medicapp.model

data class PrincipioActivo(
    val id: Int,
    val nombre: String,
    val cantidad: String? = null,
    val unidad: String? = null,
)
