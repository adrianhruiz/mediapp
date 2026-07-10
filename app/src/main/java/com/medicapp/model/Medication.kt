package com.medicapp.model

data class Medication(
    val nregistro: String,
    val nombre: String,
    val labtitular: String?,
    val dosis: String?,
    val comercializado: Boolean?,
    val requiereReceta: Boolean?,
    val fotoUrl: String?,
)
