package com.medicapp.domain

object DoseScheduleCalculator {

    fun computeDoseTimes(startAtMillis: Long, intervalHours: Int, totalDoses: Int): List<Long> {
        val intervalMillis = intervalHours * 3_600_000L
        return (0 until totalDoses).map { index -> startAtMillis + index * intervalMillis }
    }

    /**
     * Hora de la última toma. Se guarda en la pauta para poder pedir a Firestore solo las pautas
     * que tocan un día o una semana, en vez de traerlas todas y mirar las fechas en el móvil.
     */
    fun endAtMillis(startAtMillis: Long, intervalHours: Int, totalDoses: Int): Long =
        startAtMillis + (totalDoses - 1).coerceAtLeast(0) * intervalHours * 3_600_000L

    /** Cuánto por delante de ahora se mantienen creadas las tomas de una pauta sin fin. */
    const val OPEN_ENDED_WINDOW_MILLIS = 7 * 24 * 3_600_000L

    /**
     * Tomas con las que arranca (o se regenera al editarla) una pauta sin fin: desde
     * [firstAtMillis] hasta los próximos siete días, y siempre al menos una por delante de
     * [nowMillis], aunque la frecuencia sea más larga que la ventana o la primera toma caiga
     * después.
     *
     * No se crean todas de golpe porque no hay última: se van reponiendo con
     * [openEndedReplenish] al marcar tomas y al reprogramar alarmas.
     */
    fun openEndedWindow(firstAtMillis: Long, intervalHours: Int, nowMillis: Long): List<Long> {
        val step = intervalHours * 3_600_000L
        val horizon = nowMillis + OPEN_ENDED_WINDOW_MILLIS
        val times = mutableListOf<Long>()
        var next = firstAtMillis
        while (next < horizon || times.none { it > nowMillis }) {
            times += next
            next += step
        }
        return times
    }

    /**
     * Tomas que faltan tras [lastAtMillis] (la última creada) para volver a cubrir los próximos
     * siete días, con al menos una por delante de [nowMillis].
     *
     * Si la app pasó más de la ventana sin abrirse, los huecos que ya pasaron no se inventan:
     * nunca tuvieron alarma ni se vieron en Hoy, y crearlos de golpe llenaría las atrasadas de
     * tomas de las que nadie avisó. Se sigue al mismo ritmo desde el primer hueco por delante.
     */
    fun openEndedReplenish(lastAtMillis: Long, intervalHours: Int, nowMillis: Long): List<Long> {
        val step = intervalHours * 3_600_000L
        var next = lastAtMillis + step
        if (next <= nowMillis) next += ((nowMillis - next) / step + 1) * step
        val horizon = nowMillis + OPEN_ENDED_WINDOW_MILLIS
        val times = generateSequence(next) { it + step }.takeWhile { it < horizon }.toList()
        // Sin ninguna por delante, la siguiente se crea aunque caiga fuera de la ventana.
        return if (times.isEmpty() && lastAtMillis <= nowMillis) listOf(next) else times
    }
}
