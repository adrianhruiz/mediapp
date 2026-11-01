package com.medicapp.model

data class Allergy(
    val id: String,
    val tipo: AllergyType,
    val principioActivoId: Int? = null,
    val principioActivoNombre: String? = null,
    val atcCodigo: String? = null,
    val atcNombre: String? = null,
    /** Nombre en inglés para el informe: el oficial de la OMS si lo hay, o el que escriba el usuario. */
    val nombreIngles: String? = null,
    /** El nombre en inglés lo escribió el usuario; uno oficial no se puede editar ni se reemplaza. */
    val nombreInglesManual: Boolean = false,
)
