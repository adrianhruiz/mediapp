package com.medicapp.model

import java.time.LocalDate

/** Datos del paciente que identifican el informe de alergias ante un médico. */
data class PatientProfile(
    val nombre: String,
    val fechaNacimiento: LocalDate?,
)
