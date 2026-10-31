package com.medicapp.ui.history

/** Textos del resumen de la semana: la cifra de tomas y, aparte y en coral, las que se escaparon. */
object WeekSummary {

    fun takenLabel(taken: Int, due: Int): String =
        if (due == 0) "Esta semana aún no tocaba ninguna toma" else "$taken de $due tomas"

    /** Nada si no se escapó ninguna: el coral queda solo para lo que pide atención. */
    fun missedLabel(missed: Int): String? = when (missed) {
        0 -> null
        1 -> "1 se escapó"
        else -> "$missed se escaparon"
    }
}
