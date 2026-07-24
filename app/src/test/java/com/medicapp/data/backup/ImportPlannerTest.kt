package com.medicapp.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ImportPlannerTest {

    private fun reason(id: String) = BackupReason(id = id, nombre = "Motivo $id")

    private fun dose(id: String) = BackupDose(id = id, doseNumber = 1, scheduledAtMillis = 0L, status = "PENDING")

    private fun schedule(id: String, doses: List<BackupDose> = emptyList()) = BackupSchedule(
        id = id,
        nregistro = "1",
        nombreMedicamento = "X",
        intervalHours = 24,
        totalDoses = doses.size,
        startAtMillis = 0L,
        reasonId = "r1",
        reasonNombre = "Motivo",
        status = "ACTIVE",
        doses = doses,
    )

    private fun backup(
        reasons: List<BackupReason> = emptyList(),
        schedules: List<BackupSchedule> = emptyList(),
        allergies: List<BackupAllergy> = emptyList(),
    ) = BackupFile(
        exportedAtMillis = 0L,
        reasons = reasons,
        schedules = schedules,
        allergies = allergies,
    )

    @Test
    fun `restores everything into an empty account`() {
        val file = backup(reasons = listOf(reason("r1"), reason("r2")))

        val plan = ImportPlanner.plan(file, ImportPlanner.Existing())

        assertEquals(2, plan.added)
        assertEquals(0, plan.skipped)
    }

    @Test
    fun `skips what is already there instead of duplicating it`() {
        val file = backup(reasons = listOf(reason("r1"), reason("r2")))

        val plan = ImportPlanner.plan(file, ImportPlanner.Existing(reasonIds = setOf("r1")))

        assertEquals(listOf("r2"), plan.reasons.map { it.id })
        assertEquals(1, plan.skipped)
    }

    @Test
    fun `importing the same backup twice adds nothing the second time`() {
        val file = backup(
            reasons = listOf(reason("r1")),
            schedules = listOf(schedule("s1", listOf(dose("d1"), dose("d2")))),
            allergies = listOf(BackupAllergy(id = "a1", tipo = "PRINCIPIO_ACTIVO")),
        )

        val afterFirstImport = ImportPlanner.Existing(
            allergyIds = setOf("a1"),
            reasonIds = setOf("r1"),
            scheduleIds = setOf("s1"),
            doseIdsBySchedule = mapOf("s1" to setOf("d1", "d2")),
        )
        val plan = ImportPlanner.plan(file, afterFirstImport)

        assertEquals(0, plan.added)
        assertEquals(file.totalItems, plan.skipped)
    }

    @Test
    fun `restores the doses that are missing from a schedule that already exists`() {
        val file = backup(schedules = listOf(schedule("s1", listOf(dose("d1"), dose("d2")))))

        val plan = ImportPlanner.plan(
            file,
            ImportPlanner.Existing(scheduleIds = setOf("s1"), doseIdsBySchedule = mapOf("s1" to setOf("d1"))),
        )

        assertTrue("la pauta ya estaba", plan.schedules.isEmpty())
        assertEquals(listOf("s1" to "d2"), plan.doses.map { it.first to it.second.id })
    }

    @Test
    fun `keeps each dose attached to its own schedule`() {
        val file = backup(
            schedules = listOf(
                schedule("s1", listOf(dose("d1"))),
                schedule("s2", listOf(dose("d2"))),
            ),
        )

        val plan = ImportPlanner.plan(file, ImportPlanner.Existing())

        assertEquals(listOf("s1" to "d1", "s2" to "d2"), plan.doses.map { it.first to it.second.id })
    }

    @Test
    fun `everything in the backup is either restored or skipped, never lost`() {
        val file = backup(
            reasons = listOf(reason("r1"), reason("r2")),
            schedules = listOf(schedule("s1", listOf(dose("d1"), dose("d2")))),
        )

        val plan = ImportPlanner.plan(
            file,
            ImportPlanner.Existing(reasonIds = setOf("r1"), doseIdsBySchedule = mapOf("s1" to setOf("d1"))),
        )

        assertEquals(file.totalItems, plan.added + plan.skipped)
    }
}
