package com.medicapp.data.repository

import com.medicapp.model.SavedMedication
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `each new entry also writes the name and photo of its medication`() {
        assertEquals(
            mapOf("nombre" to "XAZAL 5 mg", "fotoUrl" to "https://foto"),
            HistorySummary.medicationInfo(nombre = "XAZAL 5 mg", fotoUrl = "https://foto"),
        )
    }

    @Test
    fun `saved medications come with their name and photo, only while they have entries`() {
        val counts = mapOf<String, Any?>("51234" to 1L, "60001" to 0L)
        val medicamentos = mapOf<String, Any?>(
            "51234" to mapOf("nombre" to "XAZAL", "fotoUrl" to "https://foto"),
            "60001" to mapOf("nombre" to "DALSY", "fotoUrl" to null),
        )

        assertEquals(
            listOf(SavedMedication("51234", "XAZAL", "https://foto")),
            HistorySummary.savedMedications(counts, medicamentos),
        )
    }

    @Test
    fun `a summary without names is rebuilt, as when it is missing`() {
        val counts = mapOf<String, Any?>("51234" to 1L)

        assertTrue(HistorySummary.shouldRebuild(exists = false, fromServer = true, counts = null, medicamentos = null))
        assertTrue(HistorySummary.shouldRebuild(exists = true, fromServer = true, counts = counts, medicamentos = null))
    }

    @Test
    fun `a rebuilt summary is not rebuilt again`() {
        val rebuilt = HistorySummary.rebuild(listOf(SavedMedication("51234", "XAZAL", null)))

        @Suppress("UNCHECKED_CAST")
        assertFalse(
            HistorySummary.shouldRebuild(
                exists = true,
                fromServer = true,
                counts = rebuilt[HistorySummary.NREGISTROS_FIELD] as Map<String, Any?>,
                medicamentos = rebuilt[HistorySummary.MEDICAMENTOS_FIELD] as Map<String, Any?>,
            ),
        )
        // Un historial vacío también deja el campo, aunque vacío: si no, se rehará en cada lectura.
        val empty = HistorySummary.rebuild(emptyList())
        @Suppress("UNCHECKED_CAST")
        assertFalse(
            HistorySummary.shouldRebuild(
                exists = true,
                fromServer = true,
                counts = empty[HistorySummary.NREGISTROS_FIELD] as Map<String, Any?>,
                medicamentos = empty[HistorySummary.MEDICAMENTOS_FIELD] as Map<String, Any?>,
            ),
        )
    }

    @Test
    fun `nothing is rebuilt until the server has answered`() {
        assertFalse(HistorySummary.shouldRebuild(exists = false, fromServer = false, counts = null, medicamentos = null))
    }

    @Test
    fun `a medication counted but without a name triggers the rebuild`() {
        // Pasa si se añadió una entrada con la versión nueva antes de rehacer un resumen antiguo:
        // el merge crea el campo con un solo medicamento y los demás se quedarían sin nombre.
        val counts = mapOf<String, Any?>("51234" to 1L, "60001" to 2L)
        val medicamentos = mapOf<String, Any?>("60001" to mapOf("nombre" to "DALSY"))

        assertTrue(HistorySummary.shouldRebuild(exists = true, fromServer = true, counts = counts, medicamentos = medicamentos))
    }

    @Test
    fun `rebuilding keeps counts and one name and photo per medication`() {
        val rebuilt = HistorySummary.rebuild(
            listOf(
                SavedMedication("51234", "XAZAL", null),
                SavedMedication("51234", "XAZAL", "https://foto"),
                SavedMedication("60001", "DALSY", null),
            ),
        )

        assertEquals(mapOf("51234" to 2L, "60001" to 1L), rebuilt[HistorySummary.NREGISTROS_FIELD])
        assertEquals(
            mapOf(
                "51234" to mapOf("nombre" to "XAZAL", "fotoUrl" to "https://foto"),
                "60001" to mapOf("nombre" to "DALSY", "fotoUrl" to null),
            ),
            rebuilt[HistorySummary.MEDICAMENTOS_FIELD],
        )
    }
}
