package com.medicapp.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class MedicationPhotosTest {

    @Test
    fun `a CIMA thumbnail maps to the full size photo on the same path`() {
        assertEquals(
            "https://cima.aemps.es/cima/fotos/full/materialas/51347/51347_materialas.jpg",
            MedicationPhotos.fullSize("https://cima.aemps.es/cima/fotos/thumbnails/materialas/51347/51347_materialas.jpg"),
        )
    }

    @Test
    fun `a URL that is not a CIMA thumbnail is returned untouched`() {
        val url = "https://example.com/caja.jpg"
        assertEquals(url, MedicationPhotos.fullSize(url))
    }
}
