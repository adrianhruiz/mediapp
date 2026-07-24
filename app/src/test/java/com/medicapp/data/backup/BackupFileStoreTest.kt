package com.medicapp.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class BackupFileStoreTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun `writes the copy and can find it afterwards`() {
        val store = BackupFileStore(tempFolder.newFolder())

        store.write("medicapp-copia-2026-07-24.json", """{"version":1}""")

        val copies = store.existingCopies()
        assertEquals(1, copies.size)
        assertEquals("""{"version":1}""", copies.single().readText())
    }

    @Test
    fun `overwrites the copy of the same day instead of piling them up`() {
        val store = BackupFileStore(tempFolder.newFolder())

        store.write("medicapp-copia-2026-07-24.json", "primera")
        store.write("medicapp-copia-2026-07-24.json", "segunda")

        assertEquals(1, store.existingCopies().size)
        assertEquals("segunda", store.existingCopies().single().readText())
    }

    @Test
    fun `keeps only the most recent copies`() {
        val store = BackupFileStore(tempFolder.newFolder(), copiesKept = 3)

        (1..5).forEach { day -> store.write("medicapp-copia-2026-07-0$day.json", "dia $day") }

        assertEquals(
            listOf("medicapp-copia-2026-07-03.json", "medicapp-copia-2026-07-04.json", "medicapp-copia-2026-07-05.json"),
            store.existingCopies().map { it.name },
        )
    }

    @Test
    fun `leaves alone files that are not copies`() {
        val folder = tempFolder.newFolder()
        val store = BackupFileStore(folder, copiesKept = 1)
        store.write("medicapp-copia-2026-07-01.json", "vieja")
        val intruder = java.io.File(folder, "copias/notas.txt").apply { writeText("no tocar") }

        store.write("medicapp-copia-2026-07-02.json", "nueva")

        assertTrue(intruder.exists())
        assertEquals(listOf("medicapp-copia-2026-07-02.json"), store.existingCopies().map { it.name })
    }

    @Test
    fun `reports no copies when nothing has been written yet`() {
        assertTrue(BackupFileStore(tempFolder.newFolder()).existingCopies().isEmpty())
    }
}
