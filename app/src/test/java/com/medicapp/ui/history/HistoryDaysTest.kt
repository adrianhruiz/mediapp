package com.medicapp.ui.history

import com.medicapp.model.TakenEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryDaysTest {

    private val madrid = ZoneId.of("Europe/Madrid")
    private val today = LocalDate.of(2026, 11, 11)

    private fun entry(id: String, year: Int, month: Int, day: Int, hour: Int = 12) = TakenEntry(
        id = id,
        nregistro = "1",
        nombreMedicamento = "Ibuprofeno",
        reasonId = "r1",
        reasonNombre = "Dolor",
        timestampMillis = LocalDateTime.of(year, month, day, hour, 0).atZone(madrid).toInstant().toEpochMilli(),
        dosisSnapshot = null,
        fotoUrl = null,
    )

    @Test
    fun `groups entries by day keeping their order with Hoy and Ayer first`() {
        val entries = listOf(
            entry("a", 2026, 11, 11, 8),
            entry("b", 2026, 11, 10, 21),
            entry("c", 2026, 11, 10, 14),
            entry("d", 2026, 11, 3),
            entry("e", 2025, 12, 24),
        )

        val groups = HistoryDays.group(entries, today, madrid)

        assertEquals(
            listOf("Hoy", "Ayer", "Martes, 3 de noviembre", "24 de diciembre de 2025"),
            groups.map { it.label },
        )
        assertEquals(listOf(listOf("a"), listOf("b", "c"), listOf("d"), listOf("e")), groups.map { g -> g.entries.map { it.id } })
    }

    @Test
    fun `no entries give no groups`() {
        assertEquals(emptyList<DayGroup>(), HistoryDays.group(emptyList(), today, madrid))
    }

    @Test
    fun `the row note is the hour and the reason`() {
        assertEquals("08:00 · Dolor", HistoryDays.note(entry("a", 2026, 11, 11, 8), madrid))
    }
}
