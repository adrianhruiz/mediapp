package com.medicapp.ui.history

import com.medicapp.model.TakenEntry
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

class HistoryMonthsTest {

    private val madrid = ZoneId.of("Europe/Madrid")

    private fun entry(id: String, year: Int, month: Int, day: Int) = TakenEntry(
        id = id,
        nregistro = "1",
        nombreMedicamento = "Ibuprofeno",
        reasonId = "r1",
        reasonNombre = "Dolor",
        timestampMillis = LocalDateTime.of(year, month, day, 12, 0).atZone(madrid).toInstant().toEpochMilli(),
        dosisSnapshot = null,
        fotoUrl = null,
    )

    @Test
    fun `groups entries by month keeping their order`() {
        val entries = listOf(
            entry("a", 2026, 10, 30),
            entry("b", 2026, 10, 2),
            entry("c", 2026, 9, 15),
            entry("d", 2025, 10, 1),
        )

        val groups = HistoryMonths.group(entries, madrid)

        assertEquals(listOf("Octubre 2026", "Septiembre 2026", "Octubre 2025"), groups.map { it.label })
        assertEquals(listOf(listOf("a", "b"), listOf("c"), listOf("d")), groups.map { g -> g.entries.map { it.id } })
    }

    @Test
    fun `no entries means no months`() {
        assertEquals(emptyList<MonthGroup>(), HistoryMonths.group(emptyList(), madrid))
    }
}
