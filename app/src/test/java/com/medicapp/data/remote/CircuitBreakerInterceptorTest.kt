package com.medicapp.data.remote

import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CircuitBreakerInterceptorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private var online = true
    private var now = 0L

    private fun buildClient(threshold: Int = 3, openMillis: Long = 60_000): OkHttpClient {
        val breaker = CimaCircuitBreaker(
            failureThreshold = threshold,
            openDurationMillis = openMillis,
            nowMillis = { now },
        )
        return OkHttpClient.Builder()
            .cache(Cache(tempFolder.newFolder(), 10L * 1024 * 1024))
            .addInterceptor(CircuitBreakerInterceptor(breaker, isOnline = { online }))
            .build()
    }

    private fun request() = Request.Builder().url(server.url("/medicamentos?nombre=ibuprofeno")).build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        online = true
        now = 0L
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `passes requests through while the service answers`() {
        val client = buildClient()
        server.enqueue(MockResponse().setBody("""{"value":1}"""))

        client.newCall(request()).execute().use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `stops calling the service after repeated server errors`() {
        val client = buildClient(threshold = 3)
        repeat(3) { server.enqueue(MockResponse().setResponseCode(503)) }

        repeat(3) { client.newCall(request()).execute().close() }
        assertEquals(3, server.requestCount)

        // Con el circuito abierto y nada en caché, falla al instante sin tocar la red.
        assertThrows(CimaUnavailableException::class.java) {
            client.newCall(request()).execute()
        }
        assertEquals(3, server.requestCount)
    }

    @Test
    fun `client errors do not open the circuit`() {
        val client = buildClient(threshold = 3)
        repeat(4) { server.enqueue(MockResponse().setResponseCode(404)) }

        repeat(4) { client.newCall(request()).execute().close() }

        assertEquals(4, server.requestCount)
    }

    @Test
    fun `serves the cached response while the circuit is open`() {
        val client = buildClient(threshold = 1)
        server.enqueue(
            MockResponse()
                .setHeader("Cache-Control", "public, max-age=1")
                .setBody("""{"value":1}"""),
        )
        client.newCall(request()).execute().use { it.body.string() }

        // Que el max-age de 1 segundo caduque de verdad; si no, la siguiente llamada saldría
        // de caché, nunca llegaría el 503 y el circuito no llegaría a abrirse.
        Thread.sleep(1_200)

        server.enqueue(MockResponse().setResponseCode(503))
        client.newCall(request()).execute().close()
        assertEquals(2, server.requestCount)

        client.newCall(request()).execute().use { response ->
            assertEquals(200, response.code)
            assertEquals("""{"value":1}""", response.body.string())
        }
        assertEquals("no se ha vuelto a llamar al servicio", 2, server.requestCount)
    }

    @Test
    fun `tries again once the open window elapses`() {
        val client = buildClient(threshold = 1, openMillis = 60_000)
        server.enqueue(MockResponse().setResponseCode(503))
        client.newCall(request()).execute().close()

        now += 60_000
        server.enqueue(MockResponse().setBody("""{"value":1}"""))
        client.newCall(request()).execute().use { response ->
            assertEquals(200, response.code)
        }

        assertEquals(2, server.requestCount)
    }

    @Test
    fun `being offline does not count against the service`() {
        val client = buildClient(threshold = 1)
        online = false

        // Sin red la llamada falla, pero eso no es culpa de CIMA: el circuito sigue cerrado.
        server.shutdown()
        runCatching { client.newCall(request()).execute().close() }

        online = true
        server = MockWebServer()
        server.start()
        server.enqueue(MockResponse().setBody("""{"value":1}"""))
        client.newCall(request()).execute().use { response ->
            assertEquals(200, response.code)
        }
    }
}
