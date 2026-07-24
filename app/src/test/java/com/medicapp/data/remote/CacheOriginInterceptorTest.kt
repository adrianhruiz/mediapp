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
    fun `tags a response served straight from the cache`() {
        val client = buildClient()
        server.enqueue(
            MockResponse()
                .setHeader("Cache-Control", "public, max-age=600")
                .setBody("""{"value":1}"""),
        )
        client.newCall(request()).execute().use { it.body.string() }

        client.newCall(request()).execute().use { response ->
            assertNotNull(response.header(HEADER_SERVED_FROM_CACHE))
            assertEquals("""{"value":1}""", response.body.string())
        }

        assertEquals("la segunda no ha tocado la red", 1, server.requestCount)
    }
}
