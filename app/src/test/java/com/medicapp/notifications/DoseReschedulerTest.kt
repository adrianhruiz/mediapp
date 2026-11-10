package com.medicapp.notifications

import android.app.AlarmManager
import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.medicapp.data.repository.FakeScheduleRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = Application::class)
class DoseReschedulerTest {

    private val hour = 3_600_000L
    private val day = 24 * hour
    private val now = 1_000_000_000_000L

    private val context: Application get() = ApplicationProvider.getApplicationContext()
    private val alarms get() = shadowOf(context.getSystemService(AlarmManager::class.java))
    private val schedules = FakeScheduleRepository()

    @Before
    fun setUp() {
        ShadowAlarmManager.setCanScheduleExactAlarms(true)
    }

    private suspend fun create(openEnded: Boolean) = schedules.createSchedule(
        nregistro = "1", nombreMedicamento = "Vitamina D", fotoUrl = null, intervalHours = 24,
        totalDoses = 3, startAtMillis = now, reasonId = "r1", reasonNombre = "Déficit",
        openEnded = openEnded, nowMillis = now,
    )

    @Test
    fun `rescheduling replenishes open-ended schedules and gives the new doses an alarm`() = runTest {
        val (schedule, _) = create(openEnded = true)
        val later = now + 3 * day + hour

        DoseRescheduler.rescheduleFutureDoses(context, schedules, nowMillis = later)

        val doses = schedules.dosesSnapshot(schedule.id)
        // La ventana llega hasta later + 7 d, así que entra la toma de now + 10 d.
        assertEquals((0..10).map { now + it * day }, doses.map { it.scheduledAtMillis })
        assertEquals((8..11).toList(), doses.takeLast(4).map { it.doseNumber })
        // Alarmas para las que aún no han pasado: de now + 4 d en adelante.
        assertEquals((4..10).map { now + it * day }, alarms.scheduledAlarms.map { it.triggerAtTime }.sorted())
    }

    @Test
    fun `rescheduling a fixed schedule creates no new doses`() = runTest {
        val (schedule, _) = create(openEnded = false)

        DoseRescheduler.rescheduleFutureDoses(context, schedules, nowMillis = now + hour)

        assertEquals(3, schedules.dosesSnapshot(schedule.id).size)
        assertEquals(listOf(now + day, now + 2 * day), alarms.scheduledAlarms.map { it.triggerAtTime }.sorted())
    }
}
