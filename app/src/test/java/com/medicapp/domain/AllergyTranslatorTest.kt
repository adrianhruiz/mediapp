package com.medicapp.domain

import com.medicapp.data.repository.FakeAllergyRepository
import com.medicapp.data.repository.FakeAtcEnglishNameSource
import com.medicapp.model.Allergy
import com.medicapp.model.AllergyType
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AllergyTranslatorTest {

    /**
     * Arranca el traductor como hijo del test, para que avance con advanceUntilIdle, y lo para
     * antes de acabar: escucha las alergias sin fin y runTest esperaría por él.
     */
    private suspend fun TestScope.withTranslator(
        repository: FakeAllergyRepository,
        source: FakeAtcEnglishNameSource,
        block: suspend () -> Unit,
    ) {
        val job = launch { AllergyTranslator(repository, source).start(this) }
        advanceUntilIdle()
        block()
        job.cancel()
    }

    private val nsaids = Allergy(id = "f1", tipo = AllergyType.FAMILIA, atcCodigo = "M01A", atcNombre = "PRODUCTOS ANTIINFLAMATORIOS")
    private val metamizol = Allergy(
        id = "i1",
        tipo = AllergyType.PRINCIPIO_ACTIVO,
        principioActivoId = 111,
        principioActivoNombre = "METAMIZOL MAGNESICO",
    )

    @Test
    fun `families get the English name of their exact ATC code and active substances are left alone`() = runTest {
        val repository = FakeAllergyRepository(listOf(nsaids, metamizol))
        val source = FakeAtcEnglishNameSource(mapOf("M01A" to "ANTIINFLAMMATORY AND ANTIRHEUMATIC PRODUCTS, NON-STEROIDS"))

        withTranslator(repository, source) {
            val byId = repository.allergies.associateBy { it.id }
            assertEquals("ANTIINFLAMMATORY AND ANTIRHEUMATIC PRODUCTS, NON-STEROIDS", byId.getValue("f1").nombreIngles)
            assertNull(byId.getValue("i1").nombreIngles)
            assertEquals(listOf("M01A"), source.requested)
        }
    }

    @Test
    fun `nothing is stored when there is no exact translation`() = runTest {
        val repository = FakeAllergyRepository(listOf(nsaids))

        withTranslator(repository, FakeAtcEnglishNameSource(emptyMap())) {
            assertNull(repository.allergies.single().nombreIngles)
        }
    }

    @Test
    fun `a family that already has its English name is not looked up again`() = runTest {
        val repository = FakeAllergyRepository(listOf(nsaids.copy(nombreIngles = "NSAIDS")))
        val source = FakeAtcEnglishNameSource(mapOf("M01A" to "OTHER"))

        withTranslator(repository, source) {
            assertEquals("NSAIDS", repository.allergies.single().nombreIngles)
            assertEquals(emptyList<String>(), source.requested)
        }
    }

    @Test
    fun `a family added later is translated too`() = runTest {
        val repository = FakeAllergyRepository()
        val source = FakeAtcEnglishNameSource(mapOf("J01C" to "BETA-LACTAM ANTIBACTERIALS, PENICILLINS"))
        withTranslator(repository, source) {
            repository.addFamilia("J01C", "ANTIBACTERIANOS BETALACTAMICOS, PENICILINAS")
            advanceUntilIdle()

            assertEquals("BETA-LACTAM ANTIBACTERIALS, PENICILLINS", repository.allergies.single().nombreIngles)
        }
    }

    @Test
    fun `an English name typed by hand is never replaced`() = runTest {
        val repository = FakeAllergyRepository(listOf(nsaids.copy(nombreIngles = "NSAIDs", nombreInglesManual = true)))
        val source = FakeAtcEnglishNameSource(mapOf("M01A" to "ANTIINFLAMMATORY AND ANTIRHEUMATIC PRODUCTS, NON-STEROIDS"))

        withTranslator(repository, source) {
            assertEquals("NSAIDs", repository.allergies.single().nombreIngles)
            assertEquals(emptyList<String>(), source.requested)
        }
    }
}
