package com.medicapp.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test

class HistorySummaryTest {

    @Test
    fun `an empty summary marks nothing`() {
        assertEquals(emptySet<String>(), HistorySummary.nregistrosWithEntries(null))
        assertEquals(emptySet<String>(), HistorySummary.nregistrosWithEntries(emptyMap()))
    }

    @Test
    fun `a medication with entries stays marked`() {
        val counts = mapOf("51234" to 1L, "60001" to 3L)

        assertEquals(setOf("51234", "60001"), HistorySummary.nregistrosWithEntries(counts))
    }

    @Test
    fun `the one whose entries were all deleted stops being marked`() {
        val counts = mapOf("51234" to 0L, "60001" to 2L)

        assertEquals(setOf("60001"), HistorySummary.nregistrosWithEntries(counts))
    }

    @Test
    fun `a count gone negative does not mark anything either`() {
        // No debería pasar, pero si dos borrados se cruzan más vale no enseñar un distintivo
        // de un medicamento que ya no está en el historial.
        assertEquals(emptySet<String>(), HistorySummary.nregistrosWithEntries(mapOf("51234" to -1L)))
    }

    @Test
    fun `reads counts whatever number type Firestore hands back`() {
        val counts = mapOf<String, Any?>("51234" to 2.0, "60001" to 1, "70002" to null)

        assertEquals(setOf("51234", "60001"), HistorySummary.nregistrosWithEntries(counts))
    }

    @Test
    fun `rebuilding counts one per entry, repeats included`() {
        val nregistros = listOf("51234", "60001", "51234")

        assertEquals(
            mapOf("51234" to 2L, "60001" to 1L),
            HistorySummary.countByNregistro(nregistros),
        )
    }

    @Test
    fun `rebuilding an empty history gives an empty summary`() {
        assertEquals(emptyMap<String, Long>(), HistorySummary.countByNregistro(emptyList()))
    }
}
