package com.medicapp.data.remote

import okhttp3.Interceptor
import okhttp3.Response

/** Marca en la respuesta que no se pudo hablar con CIMA y se recurrió a la caché de disco. */
const val HEADER_SERVED_FROM_CACHE = "X-Medicapp-From-Cache"

/**
 * Etiqueta las respuestas que no han tocado la red, sea porque no había conexión o porque el
 * circuito estaba abierto. Así la UI puede avisar de que lo que enseña puede estar desfasado.
 *
 * Va como interceptor más interno de los de aplicación, para que lo vean tanto
 * [OfflineFallbackInterceptor] como [CircuitBreakerInterceptor].
 *
 * Solo cuenta cuando la petición llega forzada a `only-if-cached`, que es lo que hacen esos dos
 * interceptores al recurrir a la caché. Un acierto de caché normal (la respuesta aún no ha
 * caducado y OkHttp ni pregunta) no se marca: con red y CIMA disponibles, eso no es "sin
 * acceso", y marcarlo hacía salir el aviso al volver a la pestaña de Buscar.
 *
 * Una revalidación con 304 tampoco cuenta: ahí sí se ha hablado con el servidor y ha confirmado
 * que lo guardado sigue vigente.
 */
class CacheOriginInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val servedAsFallback = request.cacheControl.onlyIfCached &&
            response.networkResponse == null &&
            response.cacheResponse != null
        return if (servedAsFallback) {
            response.newBuilder().header(HEADER_SERVED_FROM_CACHE, "true").build()
        } else {
            response
        }
    }
}
