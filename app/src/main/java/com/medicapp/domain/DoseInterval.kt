package com.medicapp.domain

/**
 * Intervalo entre tomas expresado en cantidad + unidad para la interfaz, y en horas para el
 * modelo.
 *
 * El modelo (`MedicationSchedule.intervalHours`), Firestore, la copia de seguridad y el cálculo
 * de tomas siguen trabajando en horas: un único entero sin ambigüedad, que ya existía antes de
 * ofrecer días y semanas, así que ninguna pauta guardada necesita migrarse. Días y semanas son
 * solo múltiplos exactos (24 y 168 h) que se convierten al teclear y al mostrar.
 *
 * Quedan fuera a propósito dos frecuencias que pedía la issue #39:
 * - "Dos veces por semana" serían 84 h, que no es múltiplo de 24: cada toma caería 12 h más
 *   tarde que la anterior y la hora del día iría saltando de mañana a noche. Con un intervalo
 *   fijo no hay forma de expresar "lunes y jueves a la misma hora".
 * - "Una vez al mes" como mes de calendario tampoco cabe en un intervalo fijo (28, 30 o 31
 *   días). "Cada 4 semanas" cubre el caso mensual aproximado manteniendo la hora.
 */
object DoseInterval {

    /** Unidades que se ofrecen en el formulario, cada una con su equivalente en horas. */
    enum class Unit(val hours: Int) {
        HOURS(1),
        DAYS(24),
        WEEKS(168),
    }

    /** Intervalos de los chips rápidos, en horas: 8 h, 12 h, 1 y 2 días, 1, 2 y 4 semanas. */
    val presets: List<Int> = listOf(8, 12, 24, 48, 168, 336, 672)

    /**
     * Horas equivalentes, o null si la cuenta no cabe en un Int: una cantidad absurda en
     * semanas desbordaría a cero o a negativo y pasaría por un intervalo válido.
     */
    fun toHours(amount: Int, unit: Unit): Int? =
        runCatching { Math.multiplyExact(amount, unit.hours) }.getOrNull()

    /**
     * Descompone unas horas en la unidad mayor que las divida exacto: 168 → 1 semana, 48 → 2
     * días, 36 → 36 horas. Así una pauta guardada se carga en el formulario tal y como la habría
     * tecleado quien la creó.
     */
    fun split(hours: Int): Pair<Int, Unit> {
        val unit = Unit.entries.lastOrNull { hours > 0 && hours % it.hours == 0 } ?: Unit.HOURS
        return (hours / unit.hours) to unit
    }

    /** Texto legible para listas y detalle: "Cada 8 h", "Cada día", "Cada 2 semanas". */
    fun label(hours: Int): String {
        val (amount, unit) = split(hours)
        return when (unit) {
            Unit.HOURS -> "Cada $amount h"
            Unit.DAYS -> if (amount == 1) "Cada día" else "Cada $amount días"
            Unit.WEEKS -> if (amount == 1) "Cada semana" else "Cada $amount semanas"
        }
    }
}
