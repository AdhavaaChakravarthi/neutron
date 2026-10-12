package com.adhavaa.commander.orders

import com.adhavaa.commander.data.Settings
import com.adhavaa.commander.model.UsualSchedule
import com.adhavaa.commander.model.ordersFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class RemindersTest {
    private val fmt: (LocalDateTime) -> String = { it.format(DateTimeFormatter.ofPattern("HH:mm")) }
    private val day = LocalDate.of(2026, 10, 12)
    private val steady = ordersFor(day, UsualSchedule(LocalTime.of(23, 30), LocalTime.of(7, 30)), needH = 8.0)
    private val settings = Settings(
        onboarded = true, bedtime = LocalTime.of(23, 30), wake = LocalTime.of(7, 30), targetWake = null, needMinutes = 480,
    )

    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m)

    @Test
    fun defaultRemindersFireAtTheirOrders() {
        val r = reminders(steady, DEFAULT_REMINDERS, fmt).associateBy { it.kind }
        assertEquals(setOf(OrderKind.WAKE, OrderKind.PEAK, OrderKind.CAFFEINE, OrderKind.WIND_DOWN, OrderKind.LIGHTS_OUT), r.keys)
        assertEquals(at(12, 7, 30), r.getValue(OrderKind.WAKE).at)
        assertEquals(at(12, 22, 15), r.getValue(OrderKind.WIND_DOWN).at)
        assertEquals(at(12, 23, 15), r.getValue(OrderKind.LIGHTS_OUT).at)
    }

    @Test
    fun caffeineWarningComesThirtyMinutesEarly() {
        val c = reminders(steady, setOf(OrderKind.CAFFEINE), fmt).single()
        assertEquals(at(12, 14, 45), c.at)
        assertTrue(c.title, c.title.contains("15:15"))
    }

    @Test
    fun disabledRemindersAreNotSent() {
        assertTrue(reminders(steady, emptySet(), fmt).isEmpty())
        assertFalse(reminders(steady, DEFAULT_REMINDERS, fmt).any { it.kind == OrderKind.MELATONIN })
    }

    @Test
    fun upcomingSkipsThePastAndRollsIntoTomorrow() {
        val up = upcomingReminders(settings, at(12, 22, 30), fmt)
        assertEquals(OrderKind.LIGHTS_OUT, up.first().kind)
        assertEquals(at(12, 23, 15), up.first().at)
        assertEquals(at(13, 7, 30), up[1].at)
        assertTrue(up.all { it.at.isAfter(at(12, 22, 30)) })
        assertEquals(up.sortedBy { it.at }, up)
    }

    @Test
    fun afterMidnightTheNightStillBelongsToYesterday() {
        val late = settings.copy(bedtime = LocalTime.of(1, 0), wake = LocalTime.of(7, 0))
        val up = upcomingReminders(late, at(13, 0, 10), fmt)
        assertEquals(OrderKind.LIGHTS_OUT, up.first().kind)
        assertEquals(at(13, 0, 30), up.first().at)
    }

    @Test
    fun nothingBeforeOnboarding() {
        assertTrue(upcomingReminders(settings.copy(onboarded = false), at(12, 10), fmt).isEmpty())
    }

    @Test
    fun reminderTextHasNoMedicalClaims() {
        val banned = listOf("diagnos", "treat", "disorder", "insomnia", "apnea", "cure")
        for (r in reminders(steady, REMINDABLE.toSet(), fmt)) {
            val text = (r.title + r.text).lowercase()
            for (b in banned) assertFalse("${r.kind} mentions '$b'", text.contains(b))
        }
    }

    @Test
    fun countdownAndPhase() {
        assertEquals("T-0:45", countdown(at(12, 14, 30), at(12, 15, 15)))
        assertEquals("T-2:01", countdown(at(12, 13, 14, ), at(12, 15, 15)))
        assertEquals("Sharpest", phase(steady, at(12, 16)))
        assertEquals("Wind down", phase(steady, at(12, 22, 40)))
    }

    @Test
    fun campaignCountsSevenMonths() {
        val c = campaign(LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 12))
        assertEquals(1, c.day)
        assertEquals(212, c.totalDays)
        assertEquals(LocalDate.of(2027, 5, 12), c.end)
        assertTrue(campaign(LocalDate.of(2026, 10, 12), LocalDate.of(2027, 5, 13)).complete)
    }

    @Test
    fun nextOrderIsTheFirstUpcoming() {
        val list = buildOrders(steady, fmt)
        assertEquals(OrderKind.CAFFEINE, nextOrder(list, at(12, 14))?.kind)
        assertEquals(OrderKind.TOMORROW, nextOrder(list, at(12, 23, 30))?.kind)
    }
}
