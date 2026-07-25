package com.medicapp.data.remote

import okhttp3.Interceptor
import okhttp3.Response

private const val RETRY_DELAY_MILLIS = 500L

/**
 * Reintenta **una sola vez**, medio segundo después, las peticiones que CIMA contesta con un
 * error suyo (5xx).
 *
 * La mayoría de esos errores son pasajeros —un reinicio, un pico de carga— y se arreglan solos
 * en el siguiente intento; volver a preguntar sale más barato que enseñar un error al usuario y
 * que sea él quien repita la búsqueda.
 *
 * Un solo reintento y no más: si CIMA está caído de verdad, insistir solo alarga la espera. De
 * eso ya se encarga [CircuitBreakerInterceptor], que envuelve a este y por tanto cuenta el
 * resultado final; así un fallo que se recupera al reintentar no acerca el circuito a abrirse.
 *
 * No se reintenta nada que no haya llegado a la red: el 504 que fabrica OkHttp cuando se le pide
 * que responda solo desde caché no es un fallo del servicio, y repetirlo daría igual.
 */
class RetryInterceptor(
    private val delayMillis: Long = RETRY_DELAY_MILLIS,
    private val sleep: (Long) -> Unit = { millis -> Thread.sleep(millis) },
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        if (!response.isServerFailure()) return response

        response.close()
        sleep(delayMillis)
        return chain.proceed(chain.request())
    }
}

private fun Response.isServerFailure() = code >= 500 && networkResponse != null
