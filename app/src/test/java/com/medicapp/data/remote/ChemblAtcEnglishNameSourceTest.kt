package com.medicapp.data.remote

import android.app.Application
import com.medicapp.data.remote.dto.ChemblAtcClassDto
import com.medicapp.data.remote.dto.ChemblAtcListDto
import com.medicapp.data.repository.AtcLookup
import kotlinx.coroutines.test.runTest
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import retrofit2.HttpException
import retrofit2.Response
import java.io.IOException

// Robolectric por el Log de los fallos, que en un test JVM puro lanza.
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class ChemblAtcEnglishNameSourceTest {

    private val row = ChemblAtcClassDto(
        level1 = "M", level1Description = "MUSCULO-SKELETAL SYSTEM",
        level2 = "M01", level2Description = "ANTIINFLAMMATORY AND ANTIRHEUMATIC PRODUCTS",
        level3 = "M01A", level3Description = "ANTIINFLAMMATORY AND ANTIRHEUMATIC PRODUCTS, NON-STEROIDS",
        level4 = "M01AB", level4Description = "Acetic acid derivatives and related substances",
        level5 = "M01AB05", whoName = "diclofenac",
    )

    private class FakeChemblApi(
        private val byCode: Map<String, ChemblAtcClassDto> = emptyMap(),
        private val list: List<ChemblAtcClassDto> = emptyList(),
        private val error: Exception? = null,
    ) : ChemblApiService {
        val filters = mutableListOf<Map<String, String>>()

        override suspend fun atcClass(code: String): ChemblAtcClassDto {
            error?.let { throw it }
            return byCode[code] ?: throw HttpException(Response.error<Any>(404, "".toResponseBody()))
        }

        override suspend fun atcClasses(filter: Map<String, String>): ChemblAtcListDto {
            error?.let { throw it }
            filters += filter
            return ChemblAtcListDto(list)
        }
    }

    @Test
    fun `a level 5 code is read from its own row`() = runTest {
        val source = ChemblAtcEnglishNameSource(FakeChemblApi(byCode = mapOf("M01AB05" to row)))

        assertEquals(AtcLookup.Found("diclofenac"), source.englishName("m01ab05"))
    }

    @Test
    fun `a group code filters by its level`() = runTest {
        val api = FakeChemblApi(list = listOf(row))

        assertEquals(
            AtcLookup.Found("ANTIINFLAMMATORY AND ANTIRHEUMATIC PRODUCTS, NON-STEROIDS"),
            ChemblAtcEnglishNameSource(api).englishName("M01A"),
        )
        assertEquals(listOf(mapOf("level3" to "M01A", "limit" to "1")), api.filters)
    }

    @Test
    fun `a code ChEMBL does not know has no equivalent`() = runTest {
        assertEquals(AtcLookup.NotFound, ChemblAtcEnglishNameSource(FakeChemblApi()).englishName("M01AB99"))
        assertEquals(AtcLookup.NotFound, ChemblAtcEnglishNameSource(FakeChemblApi()).englishName("M01X"))
        assertEquals(AtcLookup.NotFound, ChemblAtcEnglishNameSource(FakeChemblApi()).englishName("../x"))
    }

    @Test
    fun `a network or server error is a failure to retry, not a missing translation`() = runTest {
        assertEquals(AtcLookup.Failed, ChemblAtcEnglishNameSource(FakeChemblApi(error = IOException("offline"))).englishName("M01A"))
        val serverError = HttpException(Response.error<Any>(503, "".toResponseBody()))
        assertEquals(AtcLookup.Failed, ChemblAtcEnglishNameSource(FakeChemblApi(error = serverError)).englishName("M01AB05"))
    }
}
