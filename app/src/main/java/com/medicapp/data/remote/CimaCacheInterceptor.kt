package com.medicapp.data.remote

import okhttp3.Interceptor
import okhttp3.Response

object CimaCacheTtl {
    const val SEARCH_MAX_AGE_SECONDS = 30 * 60
    const val DETAIL_MAX_AGE_SECONDS = 24 * 60 * 60
    const val MAESTRAS_MAX_AGE_SECONDS = 7 * 24 * 60 * 60
}

/**
 * CIMA responses come back with `Cache-Control: no-cache="Set-Cookie"`, a field-qualified
 * directive that only applies to the Set-Cookie header, not the body. Left as-is, that value
 * is easily misread by a generic HTTP cache as "don't cache". This rewrites it to an explicit
 * policy per endpoint so OkHttp's disk cache actually kicks in.
 */
class CimaCacheInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val response = chain.proceed(request)
        val maxAgeSeconds = maxAgeSecondsFor(request.url.pathSegments.lastOrNull()) ?: return response
        return response.newBuilder()
            .removeHeader("Pragma")
            .header("Cache-Control", "public, max-age=$maxAgeSeconds")
            .build()
    }

    private fun maxAgeSecondsFor(lastPathSegment: String?): Int? = when (lastPathSegment) {
        "medicamentos" -> CimaCacheTtl.SEARCH_MAX_AGE_SECONDS
        "medicamento" -> CimaCacheTtl.DETAIL_MAX_AGE_SECONDS
        "maestras" -> CimaCacheTtl.MAESTRAS_MAX_AGE_SECONDS
        else -> null
    }
}
