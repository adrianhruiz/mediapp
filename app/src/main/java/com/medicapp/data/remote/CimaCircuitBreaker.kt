package com.medicapp.data.remote

import java.util.concurrent.TimeUnit

private const val DEFAULT_FAILURE_THRESHOLD = 3
private val DEFAULT_OPEN_DURATION_MILLIS = TimeUnit.MINUTES.toMillis(1)

/**
 * Corta las llamadas a CIMA cuando el servicio está caído, para no dejar al usuario esperando
 * el timeout completo en cada búsqueda.
 *
 * Tres estados:
 * - **Cerrado**: se deja pasar todo y se cuentan los fallos seguidos.
 * - **Abierto**: tras [failureThreshold] fallos seguidos, durante [openDurationMillis] no se
 *   intenta siquiera la llamada.
 * - **Semiabierto**: pasada esa ventana se deja pasar *una* petición de prueba. Si va bien se
 *   cierra el circuito; si falla, vuelve a abrirse otra ventana.
 *
 * Solo cuentan como fallo los errores de red y los 5xx: un 4xx es culpa de la petición, no
 * señal de que el servicio esté caído.
 *
 * [nowMillis] se inyecta para poder probar el paso del tiempo sin esperas reales.
 */
class CimaCircuitBreaker(
    private val failureThreshold: Int = DEFAULT_FAILURE_THRESHOLD,
    private val openDurationMillis: Long = DEFAULT_OPEN_DURATION_MILLIS,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {

    private var consecutiveFailures = 0
    private var openedAtMillis: Long? = null

    /** Si el circuito está abierto, `false`; entonces no hay que tocar la red. */
    @Synchronized
    fun allowsRequest(): Boolean {
        val openedAt = openedAtMillis ?: return true
        if (nowMillis() - openedAt < openDurationMillis) return false
        // Ventana cumplida: dejamos pasar una petición de prueba y reiniciamos el reloj. Así,
        // si esa prueba nunca llega a reportar resultado, dentro de otra ventana habrá otra.
        openedAtMillis = nowMillis()
        return true
    }

    @Synchronized
    fun recordSuccess() {
        consecutiveFailures = 0
        openedAtMillis = null
    }

    @Synchronized
    fun recordFailure() {
        consecutiveFailures++
        if (consecutiveFailures >= failureThreshold) {
            openedAtMillis = nowMillis()
        }
    }
}
