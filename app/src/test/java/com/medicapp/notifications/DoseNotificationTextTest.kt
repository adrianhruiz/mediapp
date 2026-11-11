package com.medicapp.notifications

import org.junit.Assert.assertEquals
import org.junit.Test

class DoseNotificationTextTest {

    @Test
    fun `an open-ended dose shows its number without a total`() {
        assertEquals("Toma 12", DoseNotificationText.content(doseNumber = 12, totalDoses = 0, reminder = false))
        assertEquals(
            "Toma 12 · aún no la has marcada",
            DoseNotificationText.content(doseNumber = 12, totalDoses = 0, reminder = true),
        )
    }

    @Test
    fun `a fixed dose keeps showing its total`() {
        assertEquals("Toma 2 de 5", DoseNotificationText.content(doseNumber = 2, totalDoses = 5, reminder = false))
        assertEquals(
            "Toma 2 de 5 · aún no la has marcada",
            DoseNotificationText.content(doseNumber = 2, totalDoses = 5, reminder = true),
        )
    }
}
