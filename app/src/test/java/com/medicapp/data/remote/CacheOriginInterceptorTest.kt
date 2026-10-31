package com.medicapp.data.remote

import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CacheOriginInterceptorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    private fun buildClient(): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(tempFolder.newFolder(), 10L * 1024 * 1024))
        .addInterceptor(CacheOriginInterceptor())
        .build()

    private fun request() = Request.Builder().url(server.url("/medicamentos?nombre=x")).build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `does not tag a response that came from the network`() {
        val client = buildClient()
        server.enqueue(MockResponse().setBody("""{"value":1}"""))

        client.newCall(request()).execute().use { response ->
            assertNull(response.header(HEADER_SERVED_FROM_CACHE))
        }
    }

    @Test
    fun `does not tag a fresh cache hit served while online`() {
        // Volver a la pestaña de Buscar repite la búsqueda y OkHttp la sirve de la caché de
        // disco sin tocar la red porque aún no ha caducado. Eso no es estar sin acceso a CIMA.
        val client = buildClient()
        server.enqueue(
            MockResponse()
                .setHeader("Cache-Control", "public, max-age=600")
                .setBody("""{"value":1}"""),
        )
        client.newCall(request()).execute().use { it.body.string() }

        client.newCall(request()).execute().use { response ->
            assertNull(response.header(HEADER_SERVED_FROM_CACHE))
            assertEquals("""{"value":1}""", response.body.string())
        }

        assertEquals("la segunda no ha tocado la red", 1, server.requestCount)
    }

    @Test
    fun `tags a response served from the cache by the offline fallback`() {
        val cacheDir = tempFolder.newFolder()
        var online = true
        val client = OkHttpClient.Builder()
            .cache(Cache(cacheDir, 10L * 1024 * 1024))
            .addInterceptor(OfflineFallbackInterceptor(isOnline = { online }))
            .addInterceptor(CacheOriginInterceptor())
            .build()
        server.enqueue(
            MockResponse()
                .setHeader("Cache-Control", "public, max-age=600")
                .setBody("""{"value":1}"""),
        )
        client.newCall(request()).execute().use { it.body.string() }

        online = false
        client.newCall(request()).execute().use { response ->
            assertNotNull(response.header(HEADER_SERVED_FROM_CACHE))
            assertEquals("""{"value":1}""", response.body.string())
        }
    }

    @Test
    fun `tags a response served from the cache while the circuit is open`() {
        val breaker = CimaCircuitBreaker()
        val client = OkHttpClient.Builder()
            .cache(Cache(tempFolder.newFolder(), 10L * 1024 * 1024))
            .addInterceptor(CircuitBreakerInterceptor(breaker, isOnline = { true }))
            .addInterceptor(CacheOriginInterceptor())
            .build()
        server.enqueue(
            MockResponse()
                .setHeader("Cache-Control", "public, max-age=600")
                .setBody("""{"value":1}"""),
        )
        client.newCall(request()).execute().use { it.body.string() }

        repeat(10) { breaker.recordFailure() }
        client.newCall(request()).execute().use { response ->
            assertNotNull(response.header(HEADER_SERVED_FROM_CACHE))
        }
    }
}
