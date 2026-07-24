package com.medicapp.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val HTTP_GATEWAY_TIMEOUT = 504

/** Cuánto puede pasarse de su TTL una respuesta cacheada y aun así servirse. */
internal val CIMA_MAX_STALE_SECONDS = TimeUnit.DAYS.toSeconds(7)

/** No hay conexión y tampoco nada guardado con lo que salir del paso. */
class OfflineWithoutCacheException : IOException("Sin conexión y sin datos guardados")

/**
 * When offline, serves the last cached response instead of failing outright, as long as one
 * exists within [CIMA_MAX_STALE_SECONDS] of its original TTL. [isOnline] is injected rather
 * than reading connectivity directly so this class stays testable with plain JUnit.
 */
class OfflineFallbackInterceptor(
    private val isOnline: () -> Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (isOnline()) return chain.proceed(request)

        val response = chain.proceed(
            request.newBuilder()
                .header("Cache-Control", "only-if-cached, max-stale=$CIMA_MAX_STALE_SECONDS")
                .build(),
        )
        // Al forzar only-if-cached, un 504 no viene del servidor: lo fabrica OkHttp cuando no
        // hay nada guardado que servir. Devolverlo tal cual acababa como "error inesperado" en
        // la pantalla, cuando lo que pasa es que no hay conexión.
        if (response.code == HTTP_GATEWAY_TIMEOUT) {
            response.close()
            throw OfflineWithoutCacheException()
        }
        return response
    }
}
