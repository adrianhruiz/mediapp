package com.medicapp.data.backup

import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

object BackupSerializer {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(backup: BackupFile): String = json.encodeToString(backup)

    fun decode(text: String): BackupFile = json.decodeFromString(text)

    private val fileNameDateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")

    /** Nombre sugerido en el selector del sistema, con la fecha para no pisar copias previas. */
    fun suggestedFileName(atMillis: Long): String {
        val date = Instant.ofEpochMilli(atMillis).atZone(ZoneId.systemDefault()).format(fileNameDateFormatter)
        return "medicapp-copia-$date.json"
    }
}
