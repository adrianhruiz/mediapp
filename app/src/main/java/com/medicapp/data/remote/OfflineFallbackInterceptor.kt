package com.medicapp.data.remote

import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit

/** Cuánto puede pasarse de su TTL una respuesta cacheada y aun así servirse. */
internal val CIMA_MAX_STALE_SECONDS = TimeUnit.DAYS.toSeconds(7)

/**
 * When offline, serves the last cached response instead of failing outright, as long as one
 * exists within [CIMA_MAX_STALE_SECONDS] of its original TTL. [isOnline] is injected rather
 * than reading connectivity directly so this class stays testable with plain JUnit.
 */
class OfflineFallbackInterceptor(
    private val isOnline: () -> Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        if (!isOnline()) {
            request = request.newBuilder()
                .header("Cache-Control", "only-if-cached, max-stale=$CIMA_MAX_STALE_SECONDS")
                .build()
        }
        return chain.proceed(request)
    }
}
