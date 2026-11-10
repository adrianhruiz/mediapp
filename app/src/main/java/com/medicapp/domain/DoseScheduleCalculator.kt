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
     * Si pasaron más de siete días sin reponer, también se crean las de los huecos, cada una a
     * su hora: salen como atrasadas, igual que una toma que se pasó sin marcar, en vez de
     * desaparecer sin rastro. Quien las recibe solo les pone alarma a las que aún no han pasado.
     */
    fun openEndedReplenish(lastAtMillis: Long, intervalHours: Int, nowMillis: Long): List<Long> {
        val step = intervalHours * 3_600_000L
        val horizon = nowMillis + OPEN_ENDED_WINDOW_MILLIS
        val times = mutableListOf<Long>()
        var next = lastAtMillis + step
        // Hasta cubrir la ventana y, aunque caiga fuera, hasta tener una por delante.
        while (next < horizon || (times.lastOrNull() ?: lastAtMillis) <= nowMillis) {
            times += next
            next += step
        }
        return times
    }

    /**
     * Cuántas tomas se crean como mucho de una vez. Firestore admite 500 escrituras por
     * transacción y cada reposición escribe además la pauta; un hueco largo con una frecuencia
     * corta (semanas sin abrir la app, cada pocas horas) se reparte en varias.
     */
    const val MAX_DOSES_PER_REFILL = 400

    /**
     * El primer tramo de [openEndedReplenish], de [maxDoses] tomas como mucho. Repetirlo desde la
     * última toma de cada tramo hasta que salga vacío crea exactamente las mismas tomas.
     */
    fun openEndedReplenishChunk(
        lastAtMillis: Long,
        intervalHours: Int,
        nowMillis: Long,
        maxDoses: Int = MAX_DOSES_PER_REFILL,
    ): List<Long> = openEndedReplenish(lastAtMillis, intervalHours, nowMillis).take(maxDoses)
}
