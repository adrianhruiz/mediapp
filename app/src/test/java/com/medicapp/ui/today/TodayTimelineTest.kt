package com.medicapp.ui.today

import com.medicapp.model.DoseStatus
import com.medicapp.model.MedicationDose
import com.medicapp.model.MedicationSchedule
import com.medicapp.model.ScheduleStatus
import org.junit.Assert.assertEquals
import org.junit.Test

class TodayTimelineTest {

    private fun dose(id: String, at: Long, carriedOver: Boolean = false, status: DoseStatus = DoseStatus.PENDING) = TodayDose(
        schedule = MedicationSchedule(
            id = "s1", nregistro = "1", nombreMedicamento = "Amoxicilina", fotoUrl = null, intervalHours = 8,
            totalDoses = 9, startAtMillis = 0, reasonId = "r1", reasonNombre = "Faringitis",
            status = ScheduleStatus.ACTIVE,
        ),
        dose = MedicationDose(id, "s1", 1, at, status, null, null),
        overdue = status == DoseStatus.PENDING && at < 100,
        carriedOver = carriedOver,
    )

    private val old1 = dose("o1", 1, carriedOver = true)
    private val old2 = dose("o2", 2, carriedOver = true)
    private val taken = dose("t1", 50, status = DoseStatus.TAKEN)
    private val nextToday = dose("t2", 150)
    private val later = dose("t3", 200)
    private val plan = TodayPlan(doses = listOf(old1, old2, taken, nextToday, later), next = old1)

    @Test
    fun `doses from earlier days stay folded and today opens its own next dose`() {
        val timeline = TodayTimeline.of(plan, showEarlier = false, selectedId = null)

        assertEquals(listOf("t1", "t2", "t3"), timeline.visible.map { it.dose.id })
        assertEquals(2, timeline.earlierCount)
        assertEquals("t2", timeline.open?.dose?.id)
    }

    @Test
    fun `unfolding shows the earlier doses first and opens the oldest one`() {
        val timeline = TodayTimeline.of(plan, showEarlier = true, selectedId = null)

        assertEquals(listOf("o1", "o2", "t1", "t2", "t3"), timeline.visible.map { it.dose.id })
        assertEquals("o1", timeline.open?.dose?.id)
    }

    @Test
    fun `a tapped dose stays open, but not one hidden by the fold`() {
        assertEquals("t3", TodayTimeline.of(plan, showEarlier = false, selectedId = "t3").open?.dose?.id)
        assertEquals("t2", TodayTimeline.of(plan, showEarlier = false, selectedId = "o2").open?.dose?.id)
    }

    @Test
    fun `with nothing earlier the timeline is the plan as is`() {
        val todayOnly = TodayPlan(doses = listOf(taken, nextToday), next = nextToday)
        val timeline = TodayTimeline.of(todayOnly, showEarlier = false, selectedId = null)

        assertEquals(listOf("t1", "t2"), timeline.visible.map { it.dose.id })
        assertEquals(0, timeline.earlierCount)
        assertEquals("t2", timeline.open?.dose?.id)
    }

    @Test
    fun `an earlier dose is noted as just Sin tomar because its day is in the hour column`() {
        assertEquals("Sin tomar", old1.timelineNote(nowMillis = 1_000))
    }
}
