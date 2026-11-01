package com.medicapp.ui.allergies

import java.time.LocalDate

enum class ReportLanguage { ES, EN }

/**
 * Textos del informe de alergias en los dos idiomas. Los nombres de las sustancias no se
 * traducen: son los de CIMA, en español; el código ATC, de la clasificación de la OMS, es lo que
 * un médico de fuera reconoce sin traducción.
 */
data class ReportText(
    val title: String,
    val patient: String,
    val birthDate: String,
    val statement: String,
    val families: String,
    val ingredients: String,
    val noAllergies: String,
    val namesNote: String,
    private val months: List<String>,
) {
    /** Mes en letras: "03/12" se lee distinto a cada lado del Atlántico, "12 mar" no. */
    fun formatDate(date: LocalDate): String = "${date.dayOfMonth} ${months[date.monthValue - 1]} ${date.year}"

    companion object {
        private val spanish = ReportText(
            title = "Informe de alergias",
            patient = "Paciente",
            birthDate = "Fecha de nacimiento",
            statement = "Este paciente es alérgico a los siguientes medicamentos. No le administre " +
                "ninguno de ellos ni otros del mismo grupo.",
            families = "Grupos de medicamentos (código ATC)",
            ingredients = "Principios activos",
            noAllergies = "No hay alergias a medicamentos registradas.",
            namesNote = "Los códigos ATC siguen la clasificación de la OMS.",
            months = listOf("ene", "feb", "mar", "abr", "may", "jun", "jul", "ago", "sept", "oct", "nov", "dic"),
        )

        private val english = ReportText(
            title = "Allergy report",
            patient = "Patient",
            birthDate = "Date of birth",
            statement = "This patient is allergic to the following medicines. Do not administer " +
                "any of them or any other medicine from the same group.",
            families = "Drug classes (ATC code)",
            ingredients = "Active substances",
            noAllergies = "No drug allergies recorded.",
            namesNote = "Substance names are given in Spanish. ATC codes follow the WHO classification.",
            months = listOf("Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"),
        )

        fun of(language: ReportLanguage): ReportText = when (language) {
            ReportLanguage.ES -> spanish
            ReportLanguage.EN -> english
        }
    }
}
