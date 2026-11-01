package com.medicapp.data.remote

import android.util.Log
import com.medicapp.data.remote.dto.ChemblAtcClassDto
import com.medicapp.data.repository.AtcEnglishNameSource
import com.medicapp.data.repository.AtcLookup
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException

/**
 * Traduce un código ATC con el nombre que le da la OMS, y solo ese: si la fila que devuelve
 * ChEMBL no es exactamente la del código pedido, no hay traducción. Un nombre aproximado en un
 * informe de alergias es peor que dejarlo en español.
 */
object ChemblAtc {

    /**
     * Forma de cada nivel: A, A01, A01A, A01AA, A01AA01. El código va en la URL de la consulta,
     * así que lo que no encaje aquí no sale del móvil.
     */
    private val levelPatterns = listOf(
        Regex("[A-Z]"),
        Regex("[A-Z][0-9]{2}"),
        Regex("[A-Z][0-9]{2}[A-Z]"),
        Regex("[A-Z][0-9]{2}[A-Z]{2}"),
        Regex("[A-Z][0-9]{2}[A-Z]{2}[0-9]{2}"),
    )

    fun levelOf(code: String): Int? =
        levelPatterns.indexOfFirst { it.matches(code) }.takeIf { it >= 0 }?.plus(1)

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

    override suspend fun englishName(atcCode: String): AtcLookup {
        val code = atcCode.trim().uppercase()
        val level = ChemblAtc.levelOf(code) ?: return AtcLookup.NotFound
        val row = try {
            if (level == 5) {
                api.atcClass(code)
            } else {
                api.atcClasses(mapOf("level$level" to code, "limit" to "1")).atc.firstOrNull()
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: HttpException) {
            // Un 404 es que ChEMBL no tiene ese código; cualquier otro error es pasajero.
            if (e.code() == HTTP_NOT_FOUND) return AtcLookup.NotFound
            Log.w("ChemblAtc", "ChEMBL answered ${e.code()} for $code")
            return AtcLookup.Failed
        } catch (e: Exception) {
            Log.w("ChemblAtc", "Could not reach ChEMBL for $code", e)
            return AtcLookup.Failed
        }
        return row?.let { ChemblAtc.englishName(code, it) }?.let { AtcLookup.Found(it) } ?: AtcLookup.NotFound
    }

    private companion object {
        const val HTTP_NOT_FOUND = 404
    }
}
