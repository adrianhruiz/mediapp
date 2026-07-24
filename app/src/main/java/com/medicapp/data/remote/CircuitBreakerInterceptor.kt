package com.medicapp.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** CIMA está caído o no responde, y no hay nada en caché con lo que salir del paso. */
class CimaUnavailableException : IOException("El servicio de CIMA no está disponible")

/**
 * Aplica [breaker] a las llamadas a CIMA. Con el circuito abierto no se espera al timeout:
 * se intenta servir lo último que haya en caché y, si no hay nada, se falla al instante con
 * [CimaUnavailableException] para que la UI pueda decir algo más útil que "error inesperado".
 *
 * Cuando no hay red no se registra nada: eso no es un fallo de CIMA y de ello ya se ocupa
 * [OfflineFallbackInterceptor].
 */
class CircuitBreakerInterceptor(
    private val breaker: CimaCircuitBreaker,
    private val isOnline: () -> Boolean,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!isOnline()) return chain.proceed(request)

        if (!breaker.allowsRequest()) {
            val cached = chain.proceed(
                request.newBuilder()
                    .header("Cache-Control", "only-if-cached, max-stale=$CIMA_MAX_STALE_SECONDS")
                    .build(),
            )
            if (cached.isSuccessful) return cached
            cached.close()
            throw CimaUnavailableException()
        }

        val response = try {
            chain.proceed(request)
        } catch (e: IOException) {
            breaker.recordFailure()
            throw e
        }
        if (response.code >= 500) breaker.recordFailure() else breaker.recordSuccess()
        return response
    }
}
