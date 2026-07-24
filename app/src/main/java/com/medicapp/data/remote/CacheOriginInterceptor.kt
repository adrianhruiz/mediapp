package com.medicapp.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/** Marca en la respuesta que no se llegó a hablar con CIMA y salió de la caché de disco. */
const val HEADER_SERVED_FROM_CACHE = "X-Medicapp-From-Cache"

/**
 * Etiqueta las respuestas que no han tocado la red, sea porque no había conexión o porque el
 * circuito estaba abierto. Así la UI puede avisar de que lo que enseña puede estar desfasado.
 *
 * Va como interceptor más interno de los de aplicación, para que lo vean tanto
 * [OfflineFallbackInterceptor] como [CircuitBreakerInterceptor].
 *
 * Una revalidación con 304 no cuenta: ahí sí se ha hablado con el servidor y ha confirmado que
 * lo guardado sigue vigente.
 */
class CacheOriginInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val servedFromCache = response.networkResponse == null && response.cacheResponse != null
        return if (servedFromCache) {
            response.newBuilder().header(HEADER_SERVED_FROM_CACHE, "true").build()
        } else {
            response
        }
    }
}
