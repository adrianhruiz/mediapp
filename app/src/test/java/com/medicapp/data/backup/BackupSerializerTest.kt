package com.medicapp.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupSerializerTest {

    private fun sample() = BackupFile(
        exportedAtMillis = 1_700_000_000_000L,
        allergies = listOf(
            BackupAllergy(id = "a1", tipo = "PRINCIPIO_ACTIVO", principioActivoId = 7, principioActivoNombre = "IBUPROFENO"),
        ),
        reasons = listOf(BackupReason(id = "r1", nombre = "Dolor de espalda")),
        takenMedications = listOf(
            BackupTakenEntry(
                id = "t1",
                nregistro = "12345",
                nombreMedicamento = "IBUPROFENO CINFA",
                reasonId = "r1",
                reasonNombre = "Dolor de espalda",
                timestampMillis = 1_699_000_000_000L,
            ),
        ),
        schedules = listOf(
            BackupSchedule(
                id = "s1",
                nregistro = "12345",
                nombreMedicamento = "IBUPROFENO CINFA",
                intervalHours = 24,
                totalDoses = 3,
                startAtMillis = 1_699_000_000_000L,
                reasonId = "r1",
                reasonNombre = "Dolor de espalda",
                status = "ACTIVE",
                createdAtMillis = 1_699_000_000_000L,
                doses = listOf(
                    BackupDose(id = "d1", doseNumber = 1, scheduledAtMillis = 1_699_000_000_000L, status = "TAKEN"),
                    BackupDose(id = "d2", doseNumber = 2, scheduledAtMillis = 1_699_086_400_000L, status = "PENDING"),
                ),
            ),
        ),
    )

    @Test
    fun `a backup survives a round trip without losing anything`() {
        val original = sample()

        val restored = BackupSerializer.decode(BackupSerializer.encode(original))

        assertEquals(original, restored)
    }

    @Test
    fun `counts every record including the doses inside each schedule`() {
        // 1 alergia + 1 motivo + 1 toma + 1 pauta + 2 dosis
        assertEquals(6, sample().totalItems)
    }

    @Test
    fun `writes the format version so a future reader can tell what it is reading`() {
        val text = BackupSerializer.encode(sample())

        assertTrue(text.contains("\"version\": $BACKUP_FORMAT_VERSION"))
    }

    @Test
    fun `reads a file written by a later version without crashing on unknown fields`() {
        val text = """
            {
              "version": 1,
              "exportedAtMillis": 1700000000000,
              "reasons": [{"id": "r1", "nombre": "Dolor", "algoQueNoConocemos": true}]
            }
        """.trimIndent()

        val restored = BackupSerializer.decode(text)

        assertEquals("Dolor", restored.reasons.single().nombre)
    }

    @Test
    fun `suggests a file name with the export date`() {
        // 2026-07-24T12:00:00Z
        val name = BackupSerializer.suggestedFileName(1_785_240_000_000L)

        assertTrue(name, name.startsWith("medicapp-copia-2026-07-2"))
        assertTrue(name, name.endsWith(".json"))
    }

    private fun openEndedSchedule() = BackupSchedule(
        id = "s2",
        nregistro = "999",
        nombreMedicamento = "VITAMINA D",
        intervalHours = 168,
        totalDoses = 0,
        startAtMillis = 1_699_000_000_000L,
        reasonId = "r1",
        reasonNombre = "Déficit",
        status = "ACTIVE",
        openEnded = true,
        doses = listOf(
            BackupDose(id = "d1", doseNumber = 1, scheduledAtMillis = 1_699_000_000_000L, status = "TAKEN"),
            BackupDose(id = "d2", doseNumber = 2, scheduledAtMillis = 1_699_604_800_000L, status = "PENDING"),
        ),
    )

    @Test
    fun `an open-ended schedule keeps its mode through a round trip`() {
        val original = sample().copy(schedules = listOf(openEndedSchedule()))

        val restored = BackupSerializer.decode(BackupSerializer.encode(original))

        assertEquals(original, restored)
        assertTrue(restored.schedules.single().openEnded)
    }

    @Test
    fun `a backup made before open-ended schedules restores them as fixed`() {
        val json = BackupSerializer.encode(sample()).replace(Regex(""",\s*"openEnded":\s*false"""), "")
        assertTrue(!json.contains("openEnded"))

        val restored = BackupSerializer.decode(json)

        assertEquals(false, restored.schedules.single().openEnded)
    }

    @Test
    fun `restoring an open-ended schedule keeps where its doses stopped so it can replenish`() {
        val fields = openEndedSchedule().progressFields()

        assertEquals(true, fields["openEnded"])
        assertEquals(1_699_604_800_000L, fields["endAtMillis"])
        assertEquals(2, fields["lastDoseNumber"])
    }

    @Test
    fun `restoring a fixed schedule still ends at its last dose`() {
        val fields = sample().schedules.single().progressFields()

        assertEquals(false, fields["openEnded"])
        assertEquals(1_699_086_400_000L, fields["endAtMillis"])
    }
}
