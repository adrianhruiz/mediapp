package com.medicapp.notifications

/**
 * Texto del aviso de una toma. Una pauta sin fin guarda el total a 0 (no lo tiene), así que su
 * toma se nombra solo por su número: "Toma 12" en lugar de "Toma 12 de 0".
 */
object DoseNotificationText {

    fun content(doseNumber: Int, totalDoses: Int, reminder: Boolean): String {
        val dose = if (totalDoses > 0) "Toma $doseNumber de $totalDoses" else "Toma $doseNumber"
        return if (reminder) "$dose · aún no la has marcada" else dose
    }
}
