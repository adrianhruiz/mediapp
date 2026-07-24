package com.medicapp.data.backup

import java.io.File

private const val BACKUP_DIRECTORY = "copias"
private const val DEFAULT_COPIES_KEPT = 7

/**
 * Guarda las copias automáticas y va tirando las viejas.
 *
 * Estas copias viven en la carpeta externa de la app, no en la que elige el usuario: cuando la
 * copia la lanza el sistema en segundo plano no hay nadie delante para escoger destino. Eso las
 * hace útiles contra un borrado accidental, pero **desaparecen si se desinstala la app**; para
 * guardar los datos fuera del móvil está la exportación manual.
 */
class BackupFileStore(
    private val parentDirectory: File?,
    private val copiesKept: Int = DEFAULT_COPIES_KEPT,
) {

    fun write(fileName: String, contents: String): File {
        val directory = File(requireNotNull(parentDirectory) { "No hay almacenamiento disponible" }, BACKUP_DIRECTORY)
        directory.mkdirs()
        val file = File(directory, fileName)
        file.writeText(contents)
        pruneOldCopies(directory)
        return file
    }

    /**
     * Deja solo las [copiesKept] más recientes. Se ordena por nombre y no por fecha del fichero
     * porque el nombre lleva la fecha en formato yyyy-MM-dd, que ordena igual alfabéticamente y
     * no depende de la marca de tiempo del sistema de ficheros.
     */
    private fun pruneOldCopies(directory: File) {
        val copies = directory.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.sortedBy { it.name }
            ?: return
        copies.dropLast(copiesKept).forEach { it.delete() }
    }

    fun existingCopies(): List<File> {
        val directory = File(parentDirectory ?: return emptyList(), BACKUP_DIRECTORY)
        return directory.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.sortedBy { it.name }
            ?: emptyList()
    }
}
