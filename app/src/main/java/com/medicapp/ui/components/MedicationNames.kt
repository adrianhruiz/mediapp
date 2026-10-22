package com.medicapp.ui.components

/**
 * CIMA da los nombres en mayúsculas y con la presentación pegada ("IBUPROFENO CINFA 600 mg
 * COMPRIMIDOS RECUBIERTOS EFG"). En pantalla se leen mejor con mayúscula inicial y separando el
 * nombre de la dosis y la forma.
 */
object MedicationNames {

    private val lowercaseWords = setOf("de", "del", "la", "el", "los", "las", "y", "e", "en", "con", "para", "por", "sin", "a", "al", "o")
    private val units = setOf("mg", "g", "ml", "mcg", "µg", "ui", "mmol", "meq", "%")
    private val acronyms = setOf("EFG", "DH", "SL")

    /** El nombre con mayúscula inicial, sin tocar los que ya vienen escritos a mano. */
    fun pretty(nombre: String): String {
        val letters = nombre.filter(Char::isLetter)
        if (letters.isEmpty() || letters.count(Char::isLowerCase) * 2 > letters.length) return nombre.trim()
        return nombre.trim().split(Regex("\\s+")).mapIndexed { index, word -> prettyWord(word, index) }.joinToString(" ")
    }

    /**
     * El nombre comercial y, aparte, lo que viene desde el primer número (dosis y forma). Si el
     * nombre empieza por un número o no tiene ninguno, todo va en el título.
     */
    fun split(nombre: String): Pair<String, String?> {
        val words = pretty(nombre).split(" ")
        val firstNumber = words.indexOfFirst { it.firstOrNull()?.isDigit() == true }
        if (firstNumber <= 0) return words.joinToString(" ") to null
        return words.take(firstNumber).joinToString(" ") to words.drop(firstNumber).joinToString(" ")
    }

    private fun prettyWord(word: String, index: Int): String {
        val upper = word.uppercase()
        val lower = word.lowercase()
        val bare = lower.trim('(', ')', ',', '.')
        return when {
            upper.trim('(', ')', ',', '.') in acronyms -> upper
            bare in units -> lower
            word.any(Char::isDigit) -> lower
            index > 0 && bare in lowercaseWords -> lower
            else -> capitalizeFirstLetter(lower)
        }
    }

    private fun capitalizeFirstLetter(word: String): String {
        val position = word.indexOfFirst(Char::isLetter)
        if (position < 0) return word
        return word.substring(0, position) + word[position].uppercaseChar() + word.substring(position + 1)
    }
}
