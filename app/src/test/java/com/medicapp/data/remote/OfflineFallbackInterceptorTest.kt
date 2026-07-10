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

class OfflineFallbackInterceptorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private var online = true

    private fun buildClient(): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(tempFolder.newFolder(), 10L * 1024 * 1024))
        .addInterceptor(OfflineFallbackInterceptor(isOnline = { online }))
        .build()

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
        online = true
    }

    @After
    fun tearDown() {
        server.shutdown()
    }

    @Test
    fun `serves stale cached response instead of hitting the network when offline`() {
        val client = buildClient()
        val request = Request.Builder().url(server.url("/medicamento?nregistro=1")).build()

        server.enqueue(
            MockResponse()
                .setHeader("Content-Type", "application/json")
                .setHeader("Cache-Control", "public, max-age=1")
                .setBody("""{"value":1}"""),
        )
        client.newCall(request).execute().use { primed ->
            assertEquals("""{"value":1}""", primed.body.string())
        }

        // Let the 1-second max-age actually elapse so the entry is genuinely stale.
        Thread.sleep(1_200)

        online = false
        client.newCall(request).execute().use { offlineResponse ->
            assertEquals(200, offlineResponse.code)
            assertEquals("""{"value":1}""", offlineResponse.body.string())
        }

        assertEquals(1, server.requestCount)
    }

    @Test
    fun `returns 504 when offline and nothing is cached`() {
        val client = buildClient()
        online = false
        val request = Request.Builder().url(server.url("/medicamento?nregistro=1")).build()

        client.newCall(request).execute().use { response ->
            assertEquals(504, response.code)
        }

        assertEquals(0, server.requestCount)
    }

    @Test
    fun `hits the network as usual when online`() {
        val client = buildClient()
        val request = Request.Builder().url(server.url("/medicamento?nregistro=1")).build()

        server.enqueue(MockResponse().setBody("""{"value":1}"""))
        client.newCall(request).execute().use { response ->
            assertEquals("""{"value":1}""", response.body.string())
        }

        assertEquals(1, server.requestCount)
    }
}
