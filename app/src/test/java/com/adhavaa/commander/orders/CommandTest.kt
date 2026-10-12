package com.adhavaa.commander.orders

import com.adhavaa.commander.data.Settings
import com.adhavaa.commander.model.SleepKind
import com.adhavaa.commander.model.SleepRecord
import com.adhavaa.commander.model.UsualSchedule
import com.adhavaa.commander.model.ordersFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class CommandTest {
    private val fmt: (LocalDateTime) -> String = { it.format(DateTimeFormatter.ofPattern("HH:mm")) }
    private val day = LocalDate.of(2026, 10, 14)
    private val usual = UsualSchedule(LocalTime.of(23, 30), LocalTime.of(7, 30))
    private val settings = Settings(true, usual.bedtime, usual.wake, null, 480)
    private fun at(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 10, d, h, m)

    /** Phone use every few minutes while awake; nothing while asleep except one 3 a.m. check. */
    private fun screenOn(sleepStart: LocalDateTime, sleepEnd: LocalDateTime, until: LocalDateTime): List<LocalDateTime> {
        val out = ArrayList<LocalDateTime>()
        var t = sleepStart.minusHours(6)
        while (t.isBefore(until)) {
            if (t.isBefore(sleepStart) || !t.isBefore(sleepEnd)) out += t
            t = t.plusMinutes(7)
        }
        out += sleepStart.plusHours(3)
        return out.sorted()
    }

    @Test
    fun detectsTheNightFromScreenUse() {
        val window = detectionWindow(day, usual)
        // mid-sleep 03:30 is 30 min after the 03:00 reference, so the window starts at 18:30
        assertEquals(at(13, 18, 30), window.start)
        val night = detectNight(window, screenOn(at(13, 23, 47), at(14, 7, 12), at(14, 10)), at(14, 10))
        assertNotNull(night)
        assertTrue("start ${night!!.start}", kotlin.math.abs(java.time.Duration.between(at(13, 23, 47), night.start).toMinutes()) <= 10)
        assertTrue("end ${night.end}", kotlin.math.abs(java.time.Duration.between(at(14, 7, 12), night.end).toMinutes()) <= 10)
    }

    @Test
    fun noDetectionWhileStillResting() {
        val window = detectionWindow(day, usual)
        val events = screenOn(at(13, 23, 47), at(14, 9, 0), at(14, 6, 0)).filter { it.isBefore(at(14, 6, 0)) }
        assertNull(detectNight(window, events, at(14, 6, 30)))
    }

    @Test
    fun noDetectionWithoutAnyPhoneUse() {
        assertNull(detectNight(detectionWindow(day, usual), emptyList(), at(14, 12)))
    }

    @Test
    fun lateSleepersGetALaterWindow() {
        val w = detectionWindow(day, UsualSchedule(LocalTime.of(3, 0), LocalTime.of(11, 0)))
        assertEquals(at(13, 22), w.start)
        assertEquals(at(14, 19), w.end)
    }

    @Test
    fun morningReportShowsUntilLastNightIsIn() {
        val empty = CommandData(settings, emptyList(), emptySet())
        assertTrue(needsMorningReport(empty, at(14, 8)))
        assertFalse("too early", needsMorningReport(empty, at(14, 5)))
        val logged = CommandData(settings, listOf(SleepRecord(at(13, 23, 30), at(14, 7, 0), SleepKind.NIGHT)), emptySet())
        assertFalse(needsMorningReport(logged, at(14, 8)))
        val sleepless = CommandData(settings, emptyList(), setOf(day))
        assertFalse(needsMorningReport(sleepless, at(14, 8)))
        assertEquals(at(14, 8, 30), checkInTime(empty, day, at(14, 6)))
    }

    @Test
    fun nightFromHandlesBothSidesOfMidnight() {
        assertEquals(at(13, 23, 0) to at(14, 7, 0), nightFrom(day, LocalTime.of(23, 0), LocalTime.of(7, 0)))
        assertEquals(at(14, 1, 0) to at(14, 9, 0), nightFrom(day, LocalTime.of(1, 0), LocalTime.of(9, 0)))
    }

    @Test
    fun napOrderOnlyWithDebt() {
        val rested = ordersFor(day, usual, needH = 8.0, debtH = 0.5)
        assertTrue(buildOrders(rested, fmt).none { it.kind == OrderKind.NAP })
        val tired = ordersFor(day, usual, needH = 8.0, debtH = 3.0)
        val nap = buildOrders(tired, fmt).single { it.kind == OrderKind.NAP }
        assertEquals(tired.caffeineCutoff, nap.until)
        assertTrue(nap.detail.contains("20 minutes"))
    }

    @Test
    fun recordedNightsChangeThePlan() {
        val nights = (1L..7L).map { k ->
            val d = day.minusDays(k - 1)
            SleepRecord(d.minusDays(1).atTime(0, 30).plusDays(1), d.atTime(6, 30), SleepKind.NIGHT)
        }
        val data = CommandData(settings, nights, emptySet())
        val p = data.picture(day)
        assertTrue(p.stats.debtH!! > 5.0)
        assertEquals(LocalTime.of(0, 30), p.stats.habitualBed)
        assertEquals(at(15, 0, 0), p.orders.lightsOut)
    }

    @Test
    fun doneCountsOnlyTickableOrders() {
        val list = buildOrders(ordersFor(day, usual, needH = 8.0), fmt)
        assertEquals(1 to 4, doneCount(list, setOf(OrderKind.CAFFEINE, OrderKind.PEAK)))
    }

    @Test
    fun debtBandsAndKss() {
        assertEquals("Low", debtBand(1.0))
        assertEquals("Moderate", debtBand(3.0))
        assertEquals("High", debtBand(6.0))
        assertEquals("Alert", kssLabel(3))
        assertEquals("", kssLabel(4))
    }
}
