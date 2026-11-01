package com.medicapp.data.remote

import com.medicapp.data.remote.dto.ChemblAtcClassDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ChemblAtcTest {

    private val metamizole = ChemblAtcClassDto(
        level1 = "N", level1Description = "NERVOUS SYSTEM",
        level2 = "N02", level2Description = "ANALGESICS",
        level3 = "N02B", level3Description = "OTHER ANALGESICS AND ANTIPYRETICS",
        level4 = "N02BB", level4Description = "Pyrazolones",
        level5 = "N02BB02", whoName = "metamizole sodium",
    )

    @Test
    fun `each code length reads the description of its own level`() {
        assertEquals("NERVOUS SYSTEM", ChemblAtc.englishName("N", metamizole))
        assertEquals("ANALGESICS", ChemblAtc.englishName("N02", metamizole))
        assertEquals("OTHER ANALGESICS AND ANTIPYRETICS", ChemblAtc.englishName("N02B", metamizole))
        assertEquals("Pyrazolones", ChemblAtc.englishName("N02BB", metamizole))
        assertEquals("metamizole sodium", ChemblAtc.englishName("N02BB02", metamizole))
    }

    @Test
    fun `a row for another code is never used`() {
        assertNull(ChemblAtc.englishName("N02BA", metamizole))
        assertNull(ChemblAtc.englishName("N02BB01", metamizole))
    }

    @Test
    fun `codes that are not an ATC level or rows without a name give no translation`() {
        assertNull(ChemblAtc.englishName("N02BB0", metamizole))
        assertNull(ChemblAtc.englishName("N02BB", metamizole.copy(level4Description = "  ")))
        assertNull(ChemblAtc.englishName("N02BB", metamizole.copy(level4Description = null)))
    }
}
