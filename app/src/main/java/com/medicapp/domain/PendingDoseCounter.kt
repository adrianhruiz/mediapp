package com.medicapp.domain

/**
 * La pauta lleva apuntado cuántas dosis le quedan por tomar.
 *
 * Antes, cada vez que se marcaba una dosis había que preguntar a Firestore si quedaba alguna
 * pendiente, solo para saber si la pauta ya estaba terminada. Ese dato cabe en la propia pauta y
 * se mantiene restando uno: marcar una dosis pasa a ser una sola escritura, sin consulta de por
 * medio, y funciona igual sin cobertura.
 *
 * Las pautas creadas antes de esto no tienen el contador. No se migran a mano: la primera vez
 * que se marca una de sus dosis se cuentan las pendientes como siempre y se deja el contador
 * puesto, así que se arreglan solas y solo una vez.
 */
object PendingDoseCounter {

    const val FIELD = "pendingDoses"

    /** Cuántas quedan tras marcar una. Nunca por debajo de cero, aunque el contador se desajuste. */
    fun pendingAfterTaking(pendingBefore: Long): Long = (pendingBefore - 1).coerceAtLeast(0)

    /** La pauta se cierra al marcar su última dosis pendiente. */
    fun closesSchedule(pendingAfter: Long): Boolean = pendingAfter == 0L
}
