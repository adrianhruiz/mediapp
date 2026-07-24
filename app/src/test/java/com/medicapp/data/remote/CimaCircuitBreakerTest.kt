package com.medicapp.data.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CimaCircuitBreakerTest {

    private var now = 0L

    private fun breaker(threshold: Int = 3, openMillis: Long = 60_000) =
        CimaCircuitBreaker(failureThreshold = threshold, openDurationMillis = openMillis, nowMillis = { now })

    @Test
    fun `allows requests while nothing has failed`() {
        assertTrue(breaker().allowsRequest())
    }

    @Test
    fun `stays closed until the failure threshold is reached`() {
        val breaker = breaker(threshold = 3)

        breaker.recordFailure()
        breaker.recordFailure()

        assertTrue(breaker.allowsRequest())
    }

    @Test
    fun `opens once the failures reach the threshold`() {
        val breaker = breaker(threshold = 3)

        repeat(3) { breaker.recordFailure() }

        assertFalse(breaker.allowsRequest())
    }

    @Test
    fun `a success in between resets the failure count`() {
        val breaker = breaker(threshold = 3)

        breaker.recordFailure()
        breaker.recordFailure()
        breaker.recordSuccess()
        breaker.recordFailure()
        breaker.recordFailure()

        assertTrue(breaker.allowsRequest())
    }

    @Test
    fun `stays open for the whole window`() {
        val breaker = breaker(threshold = 1, openMillis = 60_000)
        breaker.recordFailure()

        now += 59_000

        assertFalse(breaker.allowsRequest())
    }

    @Test
    fun `lets a single probe through once the window elapses`() {
        val breaker = breaker(threshold = 1, openMillis = 60_000)
        breaker.recordFailure()

        now += 60_000

        assertTrue("la primera pasa como prueba", breaker.allowsRequest())
        assertFalse("la siguiente no, hasta saber cómo fue la prueba", breaker.allowsRequest())
    }

    @Test
    fun `a successful probe closes the circuit`() {
        val breaker = breaker(threshold = 1, openMillis = 60_000)
        breaker.recordFailure()
        now += 60_000
        breaker.allowsRequest()

        breaker.recordSuccess()

        assertTrue(breaker.allowsRequest())
        assertTrue(breaker.allowsRequest())
    }

    @Test
    fun `a failed probe opens a fresh window`() {
        val breaker = breaker(threshold = 1, openMillis = 60_000)
        breaker.recordFailure()
        now += 60_000
        breaker.allowsRequest()

        breaker.recordFailure()
        now += 59_000

        assertFalse(breaker.allowsRequest())
    }

    @Test
    fun `recovers on its own if a probe never reports back`() {
        val breaker = breaker(threshold = 1, openMillis = 60_000)
        breaker.recordFailure()
        now += 60_000
        breaker.allowsRequest() // se pierde: ni éxito ni fallo

        now += 60_000

        assertTrue(breaker.allowsRequest())
    }
}
