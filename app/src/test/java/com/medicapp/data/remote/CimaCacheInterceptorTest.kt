package com.medicapp.data.remote

import okhttp3.Cache
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

class CimaCacheInterceptorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var client: OkHttpClient

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        client = OkHttpClient.Builder()
            .cache(Cache(tempFolder.newFolder(), 10L * 1024 * 1024))
            .addNetworkInterceptor(CimaCacheInterceptor())
            .build()
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    private fun cimaStyleResponse(body: String) = MockResponse()
        .setHeader("Content-Type", "application/json")
        .setHeader("Cache-Control", "no-cache=\"Set-Cookie\"")
        .setBody(body)

    @Test
    fun `rewrites cache-control for medicamentos search path`() {
        server.enqueue(cimaStyleResponse("{}"))
        val request = Request.Builder().url(server.url("/medicamentos?nombre=paracetamol")).build()

        client.newCall(request).execute().use { response ->
            assertEquals("public, max-age=${CimaCacheTtl.SEARCH_MAX_AGE_SECONDS}", response.header("Cache-Control"))
        }
    }

    @Test
    fun `rewrites cache-control for medicamento detail path`() {
        server.enqueue(cimaStyleResponse("{}"))
        val request = Request.Builder().url(server.url("/medicamento?nregistro=1")).build()

        client.newCall(request).execute().use { response ->
            assertEquals("public, max-age=${CimaCacheTtl.DETAIL_MAX_AGE_SECONDS}", response.header("Cache-Control"))
        }
    }

    @Test
    fun `rewrites cache-control for maestras path`() {
        server.enqueue(cimaStyleResponse("{}"))
        val request = Request.Builder().url(server.url("/maestras?maestra=1")).build()

        client.newCall(request).execute().use { response ->
            assertEquals("public, max-age=${CimaCacheTtl.MAESTRAS_MAX_AGE_SECONDS}", response.header("Cache-Control"))
        }
    }

    @Test
    fun `leaves cache-control untouched for unrelated paths`() {
        server.enqueue(cimaStyleResponse("{}"))
        val request = Request.Builder().url(server.url("/other")).build()

        client.newCall(request).execute().use { response ->
            assertEquals("no-cache=\"Set-Cookie\"", response.header("Cache-Control"))
        }
    }

    @Test
    fun `second identical request within ttl is served from cache without hitting the network`() {
        val request = Request.Builder().url(server.url("/medicamento?nregistro=1")).build()
        server.enqueue(cimaStyleResponse("""{"value":1}"""))

        client.newCall(request).execute().use { first ->
            assertEquals("""{"value":1}""", first.body.string())
        }
        client.newCall(request).execute().use { second ->
            assertEquals("""{"value":1}""", second.body.string())
            assertEquals(true, second.networkResponse == null)
        }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `different urls are cached independently`() {
        server.enqueue(cimaStyleResponse("""{"value":1}"""))
        server.enqueue(cimaStyleResponse("""{"value":2}"""))

        client.newCall(Request.Builder().url(server.url("/medicamento?nregistro=1")).build())
            .execute().use { it.body.string() }
        client.newCall(Request.Builder().url(server.url("/medicamento?nregistro=2")).build())
            .execute().use { it.body.string() }

        assertEquals(2, server.requestCount)
    }
}
