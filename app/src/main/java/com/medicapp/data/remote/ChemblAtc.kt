package com.medicapp.data.remote

import android.util.Log
import com.medicapp.data.remote.dto.ChemblAtcClassDto
import com.medicapp.data.repository.AtcEnglishNameSource

/**
 * Traduce un código ATC con el nombre que le da la OMS, y solo ese: si la fila que devuelve
 * ChEMBL no es exactamente la del código pedido, no hay traducción. Un nombre aproximado en un
 * informe de alergias es peor que dejarlo en español.
 */
object ChemblAtc {

    /** Nivel ATC según la longitud del código: A, A01, A01A, A01AA, A01AA01. */
    fun levelOf(code: String): Int? = when (code.length) {
        1 -> 1
        3 -> 2
        4 -> 3
        5 -> 4
        7 -> 5
        else -> null
    }

    fun englishName(code: String, row: ChemblAtcClassDto): String? {
        val (rowCode, name) = when (levelOf(code)) {
            1 -> row.level1 to row.level1Description
            2 -> row.level2 to row.level2Description
            3 -> row.level3 to row.level3Description
            4 -> row.level4 to row.level4Description
            5 -> row.level5 to row.whoName
            else -> return null
        }
        if (rowCode != code) return null
        return name?.trim()?.takeIf { it.isNotEmpty() }
    }
}

class ChemblAtcEnglishNameSource(private val api: ChemblApiService) : AtcEnglishNameSource {

    override suspend fun englishName(atcCode: String): String? {
        val code = atcCode.trim().uppercase()
        val level = ChemblAtc.levelOf(code) ?: return null
        return runCatching {
            val row = if (level == 5) {
                api.atcClass(code)
            } else {
                api.atcClasses(mapOf("level$level" to code, "limit" to "1")).atc.firstOrNull()
            }
            row?.let { ChemblAtc.englishName(code, it) }
        }.onFailure { Log.w("ChemblAtc", "Could not translate $code", it) }.getOrNull()
    }
}
