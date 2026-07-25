package com.medicapp.data.remote

import okhttp3.Cache
import okhttp3.CacheControl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class RetryInterceptorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer

    /** Las esperas se anotan en vez de dormirse, para que el test no tarde medio segundo. */
    private val waits = mutableListOf<Long>()

    private fun buildClient(vararg extraInterceptors: okhttp3.Interceptor): OkHttpClient =
        OkHttpClient.Builder()
            .cache(Cache(tempFolder.newFolder(), 10L * 1024 * 1024))
            .apply { extraInterceptors.forEach { addInterceptor(it) } }
            .addInterceptor(RetryInterceptor(delayMillis = 500) { waits += it })
            .build()

    private fun request() = Request.Builder().url(server.url("/medicamentos?nombre=ibuprofeno")).build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        waits.clear()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `retries a server error and serves the good answer`() {
        val client = buildClient()
        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("""{"value":1}"""))

        client.newCall(request()).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("""{"value":1}""", response.body.string())
        }

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `waits before trying again`() {
        val client = buildClient()
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setBody("""{"value":1}"""))

        client.newCall(request()).execute().close()

        assertEquals(listOf(500L), waits)
    }

    @Test
    fun `tries again only once`() {
        val client = buildClient()
        repeat(2) { server.enqueue(MockResponse().setResponseCode(503)) }

        client.newCall(request()).execute().use { response ->
            assertEquals(503, response.code)
        }

        assertEquals("no insiste más allá del reintento", 2, server.requestCount)
    }

    @Test
    fun `does not retry when the mistake is ours`() {
        val client = buildClient()
        server.enqueue(MockResponse().setResponseCode(404))

        client.newCall(request()).execute().use { response ->
            assertEquals(404, response.code)
        }

        assertEquals(1, server.requestCount)
        assertEquals(emptyList<Long>(), waits)
    }

    @Test
    fun `does not retry a failure that never reached the network`() {
        val client = buildClient()

        // Pedir solo caché sin tener nada guardado da un 504 que fabrica OkHttp, no CIMA.
        val onlyCached = request().newBuilder().cacheControl(CacheControl.FORCE_CACHE).build()
        client.newCall(onlyCached).execute().use { response ->
            assertEquals(504, response.code)
        }

        assertEquals("no se ha llamado al servicio", 0, server.requestCount)
        assertEquals(emptyList<Long>(), waits)
    }

    @Test
    fun `a hiccup that fixes itself does not open the circuit`() {
        var now = 0L
        val breaker = CimaCircuitBreaker(failureThreshold = 1, nowMillis = { now })
        val client = buildClient(CircuitBreakerInterceptor(breaker, isOnline = { true }))

        server.enqueue(MockResponse().setResponseCode(503))
        server.enqueue(MockResponse().setBody("""{"value":1}"""))
        client.newCall(request()).execute().close()

        // El circuito solo ve el resultado final, que fue bueno: la siguiente llamada sale a la red.
        server.enqueue(MockResponse().setBody("""{"value":2}"""))
        client.newCall(request()).execute().use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(3, server.requestCount)
    }
}
